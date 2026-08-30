package com.admin.common.task;

import com.admin.common.dto.*;
import com.admin.common.lang.R;
import com.admin.common.utils.GostUtil;
import com.admin.entity.*;
import com.admin.service.*;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public class CheckGostConfigAsync {

    /** 隧道转发类型，与 ForwardServiceImpl.TUNNEL_TYPE_TUNNEL_FORWARD 保持一致 */
    private static final int TUNNEL_TYPE_TUNNEL_FORWARD = 2;

    @Resource
    private NodeService nodeService;

    @Resource
    @Lazy
    private ForwardService forwardService;

    @Resource
    @Lazy
    private SpeedLimitService speedLimitService;

    @Resource
    @Lazy
    private TunnelService tunnelService;

    @Resource
    @Lazy
    private UserTunnelService userTunnelService;

    @Resource
    @Lazy
    private AggregateNodeGroupService aggregateNodeGroupService;

    @Resource
    @Lazy
    private AggregateForwardService aggregateForwardService;



    /**
     * 清理孤立的Gost配置项
     */
    @Async
    public void cleanNodeConfigs(String node_id, GostConfigDto gostConfig) {
        System.out.println(JSONObject.toJSONString(gostConfig));
        Node node = nodeService.getById(node_id);
        if (node != null) {
            cleanOrphanedServices(gostConfig, node);
            cleanOrphanedChains(gostConfig, node);
            cleanOrphanedLimiters(gostConfig, node);
            // 同步数据库期望状态到节点：补建缺失的限流器与转发服务
            syncLimiters(gostConfig, node);
            syncMissingServices(gostConfig, node);
        }
    }

    /**
     * 清理孤立的服务
     */
    private void cleanOrphanedServices(GostConfigDto gostConfig, Node node) {
        if (gostConfig.getServices() == null) {
            return;
        }

        for (ConfigItem service : gostConfig.getServices()) {
            safeExecute(() -> {

                if (!Objects.equals(service.getName(), "web_api")){
                    String[] serviceIds = parseServiceName(service.getName());
                    if (isLegacyAggregateService(serviceIds)) {
                        cleanLegacyAggregateService(serviceIds, node, service.getName());
                        return;
                    }
                    if (serviceIds.length == 4) {
                        String forwardId = serviceIds[0];
                        String userId = serviceIds[1];
                        String userTunnelId = serviceIds[2];
                        String type = serviceIds[3];

                        if (Objects.equals(type, "tcp")) { // 只处理TCP，避免重复处理
                            Forward forward = forwardService.getById(forwardId);
                            if (forward == null) {
                                log.info("删除孤立的服务: {} (节点: {})", service.getName(), node.getId());
                                GostDto gostDto = GostUtil.DeleteService(node.getId(), forwardId + "_" + userId + "_" + userTunnelId);
                                System.out.println(gostDto);
                            }
                        }


                        if (Objects.equals(type, "tls")) {
                            Forward forward = forwardService.getById(forwardId);
                            if (forward == null) {
                                log.info("删除孤立的服务: {} (节点: {})", service.getName(), node.getId());
                                GostUtil.DeleteRemoteService(node.getId(), forwardId+"_"+userId+"_"+userTunnelId);
                            }
                        }

                    }
                }


            }, "清理服务 " + service.getName());
        }

    }

    private boolean isLegacyAggregateService(String[] serviceIds) {
        return serviceIds.length == 4 && Objects.equals(serviceIds[0], "agf");
    }

    private void cleanLegacyAggregateService(String[] serviceIds, Node node, String serviceName) {
        String type = serviceIds[3];
        if (!Objects.equals(type, "tcp")) {
            return;
        }
        AggregateForward aggregateForward = aggregateForwardService.getById(serviceIds[1]);
        if (aggregateForward == null) {
            String baseName = serviceIds[0] + "_" + serviceIds[1] + "_" + serviceIds[2];
            log.info("删除孤立的历史聚合转发服务: {} (节点: {})", serviceName, node.getId());
            GostUtil.DeleteService(node.getId(), baseName);
        }
    }

    /**
     * 清理孤立的链
     */
    private void cleanOrphanedChains(GostConfigDto gostConfig, Node node) {
        if (gostConfig.getChains() == null) {
            return;
        }
        

        for (ConfigItem chain : gostConfig.getChains()) {
            safeExecute(() -> {
                String[] serviceIds = parseServiceName(chain.getName());
                if (serviceIds.length == 4) {
                    String forwardId = serviceIds[0];
                    String userId = serviceIds[1];
                    String userTunnelId = serviceIds[2];
                    String type = serviceIds[3];
                    
                    if (Objects.equals(type, "chains")) {
                        Forward forward = forwardService.getById(forwardId);
                        if (forward == null) {
                            log.info("删除孤立的链: {} (节点: {})", chain.getName(), node.getId());
                            GostUtil.DeleteChains(node.getId(), forwardId+"_"+userId+"_"+userTunnelId);
                        }
                    }
                }
            }, "清理链 " + chain.getName());
        }
    }

    /**
     * 清理孤立的限流器
     */
    private void cleanOrphanedLimiters(GostConfigDto gostConfig, Node node) {
        if (gostConfig.getLimiters() == null) {
            return;
        }
        

        for (ConfigItem limiter : gostConfig.getLimiters()) {
            safeExecute(() -> {
                SpeedLimit speedLimit = speedLimitService.getById(limiter.getName());
                if (speedLimit == null) {
                    log.info("删除孤立的限流器: {} (节点: {})", limiter.getName(), node.getId());
                    GostUtil.DeleteLimiters(node.getId(), Long.parseLong(limiter.getName()));
                }
            }, "清理限流器 " + limiter.getName());
        }
    }

    /**
     * 同步限流器
     * 对比节点上报的限流器与数据库 speed_limit 记录，补建节点上缺失的限流器
     */
    public void syncLimiters(GostConfigDto gostConfig, Node node) {
        List<Tunnel> tunnelList = getTunnelsForNode(node.getId());
        if (tunnelList.isEmpty()) return;
        safeExecute(() -> {
            StringBuilder tunnelIds = new StringBuilder();
            for (Tunnel tunnel : tunnelList) {
                tunnelIds.append(tunnel.getId()).append(",");
            }
            String ids = tunnelIds.deleteCharAt(tunnelIds.length() - 1).toString();
            List<SpeedLimit> speedLimits = speedLimitService.list(new QueryWrapper<SpeedLimit>().in("tunnel_id", ids));
            if (speedLimits != null && !speedLimits.isEmpty()) {
                List<ConfigItem> limiters = gostConfig.getLimiters();
                List<Long> limiters_ids = new ArrayList<>();
                List<Long>  speedLimits_ids = new ArrayList<>();
                if (limiters != null){
                    for (ConfigItem limiter : limiters) {
                        limiters_ids.add(Long.valueOf(limiter.getName()));
                    }
                }
                for (SpeedLimit speedLimit : speedLimits) {
                    speedLimits_ids.add(speedLimit.getId());
                }
                List<Long> diff = new ArrayList<>(speedLimits_ids);
                diff.removeAll(limiters_ids);
                System.out.println(diff);
                if (!diff.isEmpty()) {

                    for (Long speed_id : diff) {
                        SpeedLimit speedLimit = speedLimitService.getById(speed_id);
                        if (speedLimit != null) {
                            SpeedLimitUpdateDto speedLimitUpdateDto = new SpeedLimitUpdateDto();
                            speedLimitUpdateDto.setId(speed_id);
                            speedLimitUpdateDto.setName(speedLimit.getName());
                            speedLimitUpdateDto.setSpeed(speedLimit.getSpeed());
                            speedLimitUpdateDto.setTunnelId(speedLimit.getTunnelId());
                            speedLimitUpdateDto.setTunnelName(speedLimit.getTunnelName());
                            speedLimitService.updateSpeedLimit(speedLimitUpdateDto);
                        }
                    }
                }
            }
        }, "同步限流器 ");
    }

    /**
     * 同步缺失的转发服务
     * 节点重装/换机后本地 gost.json 配置丢失，对比节点上报的服务与数据库 forward 记录，
     * 通过 updateGostServices 补建缺失的服务（update 不存在时节点端自动回退为 create）。
     * ha-min: 单节点逐条串行下发，转发数量极大时耗时较长；依赖 10 分钟配置上报周期，实时性有限。
     */
    private void syncMissingServices(GostConfigDto gostConfig, Node node) {
        List<Tunnel> tunnelList = getTunnelsForNode(node.getId());
        if (tunnelList.isEmpty()) return;
        safeExecute(() -> {
            Set<String> existingServices = new HashSet<>();
            if (gostConfig.getServices() != null) {
                for (ConfigItem service : gostConfig.getServices()) {
                    existingServices.add(service.getName());
                }
            }
            for (Tunnel tunnel : tunnelList) {
                List<Forward> forwardList = forwardService.list(new QueryWrapper<Forward>().eq("tunnel_id", tunnel.getId()));
                if (forwardList == null || forwardList.isEmpty()) continue;
                for (Forward forward : forwardList) {
                    // 只重建启用状态的转发；暂停/异常转发保持原状
                    if (forward.getStatus() == null || forward.getStatus() != 1) continue;
                    // getOne 第二参数 false：数据异常存在重复授权行时取首条，不中断整个节点的同步
                    UserTunnel userTunnel = userTunnelService.getOne(
                            new QueryWrapper<UserTunnel>().eq("user_id", forward.getUserId()).eq("tunnel_id", tunnel.getId()), false);
                    String serviceName = buildServiceName(forward, userTunnel);
                    boolean tcpExists = existingServices.contains(serviceName + "_tcp");
                    boolean udpExists = existingServices.contains(serviceName + "_udp");
                    boolean tlsExists = existingServices.contains(serviceName + "_tls");
                    if (tcpExists && udpExists && (tunnel.getType() == null || tunnel.getType() != TUNNEL_TYPE_TUNNEL_FORWARD || tlsExists)) {
                        continue;
                    }
                    // 缺失任一服务则整组重建
                    forwardService.updateForwardA(forward);
                }
            }
        }, "同步缺失服务 ");
    }

    private List<Tunnel> getTunnelsForNode(Long nodeId) {
        List<Tunnel> result = new ArrayList<>();
        List<Tunnel> tunnels = tunnelService.list();
        if (tunnels == null) {
            return result;
        }
        for (Tunnel tunnel : tunnels) {
            if (tunnelContainsNode(tunnel.getInGroupId(), tunnel.getInNodeId(), nodeId) ||
                    (tunnel.getType() != null && tunnel.getType() == TUNNEL_TYPE_TUNNEL_FORWARD && tunnelContainsNode(tunnel.getOutGroupId(), tunnel.getOutNodeId(), nodeId))) {
                result.add(tunnel);
            }
        }
        return result;
    }

    private boolean tunnelContainsNode(Long groupId, Long nodeId, Long targetNodeId) {
        if (groupId != null) {
            AggregateNodeGroup group = aggregateNodeGroupService.getById(groupId);
            return group != null && aggregateNodeGroupService.parseNodeIds(group).contains(targetNodeId);
        }
        return Objects.equals(nodeId, targetNodeId);
    }

    /**
     * 构建转发服务名，与 ForwardServiceImpl.buildServiceName 保持一致
     */
    private String buildServiceName(Forward forward, UserTunnel userTunnel) {
        int userTunnelId = (userTunnel != null) ? userTunnel.getId() : 0;
        return forward.getId() + "_" + forward.getUserId() + "_" + userTunnelId;
    }
    /**
     * 安全执行操作，捕获异常
     */
    private void safeExecute(Runnable operation, String operationDesc) {
        try {
            operation.run();
        } catch (Exception e) {
            log.info("执行操作失败: {}", operationDesc, e);
        }
    }


    /**
     * 解析服务名称
     */
    private String[] parseServiceName(String serviceName) {
        return serviceName.split("_");
    }
}
