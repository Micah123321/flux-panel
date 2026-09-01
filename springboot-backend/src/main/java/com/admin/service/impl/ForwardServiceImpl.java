package com.admin.service.impl;

import com.admin.common.dto.ForwardDto;
import com.admin.common.dto.ForwardUpdateDto;
import com.admin.common.dto.ForwardWithTunnelDto;
import com.admin.common.dto.GostDto;
import com.admin.common.lang.R;
import com.admin.common.utils.GostUtil;
import com.admin.common.utils.JwtUtil;
import com.admin.common.utils.WebSocketServer;
import com.admin.entity.*;
import com.admin.mapper.AggregateForwardMapper;
import com.admin.mapper.ForwardMapper;
import com.admin.service.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 端口转发服务实现类
 * </p>
 *
 * @since 2025-06-03
 */
@Slf4j
@Service
public class ForwardServiceImpl extends ServiceImpl<ForwardMapper, Forward> implements ForwardService {

    // 常量定义
    private static final String GOST_SUCCESS_MSG = "OK";
    private static final String GOST_NOT_FOUND_MSG = "not found";
    private static final int ADMIN_ROLE_ID = 0;
    private static final int TUNNEL_TYPE_PORT_FORWARD = 1;
    private static final int TUNNEL_TYPE_TUNNEL_FORWARD = 2;
    private static final int FORWARD_STATUS_ACTIVE = 1;
    private static final int FORWARD_STATUS_PAUSED = 0;
    private static final int FORWARD_STATUS_ERROR = -1;
    private static final int TUNNEL_STATUS_ACTIVE = 1;
    private static final int NODE_STATUS_ONLINE = 1;
    private static final String DEFAULT_STRATEGY = "round";

    private static final long BYTES_TO_GB = 1024L * 1024L * 1024L;

    @Resource
    @Lazy
    private TunnelService tunnelService;

    @Resource
    UserTunnelService userTunnelService;

    @Resource
    UserService userService;

    @Resource
    NodeService nodeService;

    @Resource
    private AggregateForwardMapper aggregateForwardMapper;

    @Resource
    private AggregateNodeGroupService aggregateNodeGroupService;


    @Override
    public R createForward(ForwardDto forwardDto) {
        // 1. 获取当前用户信息
        UserInfo currentUser = getCurrentUserInfo();

        // 2. 检查隧道是否存在和可用
        Tunnel tunnel = validateTunnel(forwardDto.getTunnelId());
        if (tunnel == null) {
            return R.err("隧道不存在");
        }
        if (tunnel.getStatus() != TUNNEL_STATUS_ACTIVE) {
            return R.err("隧道已禁用，无法创建转发");
        }
        // 3. 普通用户权限和限制检查
        UserPermissionResult permissionResult = checkUserPermissions(currentUser, tunnel, null);
        if (permissionResult.isHasError()) {
            return R.err(permissionResult.getErrorMessage());
        }

        // 4. 获取所需节点；节点组会先自动剔除离线成员。
        NodeInfo nodeInfo = getRequiredNodes(tunnel);
        if (nodeInfo.isHasError()) {
            return R.err(nodeInfo.getErrorMessage());
        }

        // 5. 分配端口
        PortAllocation portAllocation = allocatePorts(tunnel, forwardDto.getInPort());
        if (portAllocation.isHasError()) {
            return R.err(portAllocation.getErrorMessage());
        }

        // 6. 创建并保存Forward对象
        Forward forward = createForwardEntity(forwardDto, tunnel, currentUser, portAllocation);
        if (!this.save(forward)) {
            return R.err("端口转发创建失败");
        }

        // 7. 调用Gost服务创建转发
        R gostResult = createGostServices(forward, tunnel, permissionResult.getLimiter(), nodeInfo, permissionResult.getUserTunnel());

        if (gostResult.getCode() != 0) {
            this.removeById(forward.getId());
            return gostResult;
        }
        return R.ok();
    }


    @Override
    public R createForwards(List<ForwardDto> forwards) {
        if (forwards == null || forwards.isEmpty()) {
            return R.err("请提供要添加的转发列表");
        }

        List<Map<String, Object>> successList = new ArrayList<>();
        List<Map<String, Object>> failedList = new ArrayList<>();

        for (int i = 0; i < forwards.size(); i++) {
            ForwardDto forwardDto = forwards.get(i);
            int index = i + 1;
            String name = forwardDto == null ? "" : forwardDto.getName();
            String validationError = validateCreateForwardDto(forwardDto);
            if (validationError != null) {
                addCreateFailure(failedList, index, name, validationError);
                continue;
            }

            R result = createForward(forwardDto);
            if (result.getCode() == 0) {
                addCreateSuccess(successList, index, forwardDto.getName());
            } else {
                addCreateFailure(failedList, index, forwardDto.getName(), result.getMsg());
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("total", forwards.size());
        data.put("successCount", successList.size());
        data.put("failedCount", failedList.size());
        data.put("success", successList);
        data.put("failed", failedList);

        if (failedList.isEmpty()) {
            return R.ok(data);
        }

        // ha-min: 批量创建逐条复用单条创建链路，保持 GOST/DB 现有非事务语义和失败明细。
        R res = R.err("部分或全部转发创建失败");
        res.setData(data);
        return res;
    }

    private String validateCreateForwardDto(ForwardDto forwardDto) {
        if (forwardDto == null) {
            return "转发数据不能为空";
        }
        if (isBlank(forwardDto.getName())) {
            return "转发名称不能为空";
        }
        if (forwardDto.getTunnelId() == null) {
            return "隧道ID不能为空";
        }
        if (isBlank(forwardDto.getRemoteAddr())) {
            return "远程地址不能为空";
        }
        Integer inPort = forwardDto.getInPort();
        if (inPort != null && (inPort < 1 || inPort > 65535)) {
            return "端口号必须在1-65535之间";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void addCreateSuccess(List<Map<String, Object>> successList, int index, String name) {
        Map<String, Object> success = new HashMap<>();
        success.put("index", index);
        success.put("name", name);
        successList.add(success);
    }

    private void addCreateFailure(List<Map<String, Object>> failedList, int index, String name, String reason) {
        Map<String, Object> failed = new HashMap<>();
        failed.put("index", index);
        failed.put("name", name);
        failed.put("reason", reason == null ? "创建失败" : reason);
        failedList.add(failed);
    }

    @Override
    public R getAllForwards() {
        UserInfo currentUser = getCurrentUserInfo();

        List<ForwardWithTunnelDto> forwardList;
        if (currentUser.getRoleId() != ADMIN_ROLE_ID) {
            forwardList = baseMapper.selectForwardsWithTunnelByUserId(currentUser.getUserId());
        } else {
            forwardList = baseMapper.selectAllForwardsWithTunnel();
        }
        enrichForwardTunnelData(forwardList);

        return R.ok(forwardList);
    }

    private void enrichForwardTunnelData(List<ForwardWithTunnelDto> forwardList) {
        if (forwardList == null || forwardList.isEmpty()) {
            return;
        }
        Set<Long> tunnelIds = forwardList.stream()
                .map(ForwardWithTunnelDto::getTunnelId)
                .filter(Objects::nonNull)
                .map(Integer::longValue)
                .collect(Collectors.toSet());
        if (tunnelIds.isEmpty()) {
            return;
        }
        Map<Long, Tunnel> tunnelMap = tunnelService.listByIds(tunnelIds).stream()
                .collect(Collectors.toMap(Tunnel::getId, tunnel -> tunnel, (first, ignored) -> first));

        for (ForwardWithTunnelDto dto : forwardList) {
            if (dto.getTunnelId() == null) {
                continue;
            }
            Tunnel tunnel = tunnelMap.get(dto.getTunnelId().longValue());
            if (tunnel == null) {
                continue;
            }
            String strategy = tunnelStrategy(tunnel);
            dto.setTunnelStrategy(strategy);
            dto.setStrategy(strategy);

            List<Node> inNodes = resolveTunnelNodes(tunnel.getInGroupId(), tunnel.getInNodeId());
            if (!inNodes.isEmpty()) {
                dto.setEntryNodeCount(inNodes.size());
                dto.setEntryNodeNames(inNodes.stream().map(Node::getName).collect(Collectors.joining("\n")));
                dto.setInIp(inNodes.stream().map(this::firstNodeAddress).filter(addr -> !addr.isEmpty()).collect(Collectors.joining("\n")));
            }
        }
    }

    @Override
    public R updateForward(ForwardUpdateDto forwardUpdateDto) {
        // 1. 获取当前用户信息
        UserInfo currentUser = getCurrentUserInfo();
        if (currentUser.getRoleId() != ADMIN_ROLE_ID) {
            User user = userService.getById(currentUser.getUserId());
            if (user == null) return R.err("用户不存在");
            if (user.getStatus() == 0) return R.err("用户已到期或被禁用");
        }


        // 2. 检查转发是否存在
        Forward existForward = validateForwardExists(forwardUpdateDto.getId(), currentUser);
        if (existForward == null) {
            return R.err("转发不存在");
        }

        // 3. 检查隧道是否存在和可用
        Tunnel tunnel = validateTunnel(forwardUpdateDto.getTunnelId());
        if (tunnel == null) {
            return R.err("隧道不存在");
        }
        if (tunnel.getStatus() != TUNNEL_STATUS_ACTIVE) {
            return R.err("隧道已禁用，无法更新转发");
        }
        boolean tunnelChanged = isTunnelChanged(existForward, forwardUpdateDto);
        // 4. 检查权限和限制
        UserPermissionResult permissionResult = null;
        if (tunnelChanged) {
            if (currentUser.getRoleId() == ADMIN_ROLE_ID) {
                // 管理员操作自己的转发时，不需要检查权限限制
                if (Objects.equals(currentUser.getUserId(), existForward.getUserId())) {
                    permissionResult = UserPermissionResult.success(null, null);
                } else {
                    // 管理员操作用户转发时，需要检查原用户是否有新隧道权限
                    // 获取原转发用户的信息
                    User originalUser = userService.getById(existForward.getUserId());
                    if (originalUser == null) {
                        return R.err("用户不存在");
                    }

                    // 检查原用户是否有新隧道权限
                    UserTunnel userTunnel = getUserTunnel(existForward.getUserId(), tunnel.getId().intValue());
                    if (userTunnel == null) {
                        return R.err("用户没有该隧道权限");
                    }

                    if (userTunnel.getStatus() != 1) {
                        return R.err("隧道被禁用");
                    }

                    // 检查隧道权限到期时间
                    if (userTunnel.getExpTime() != null && userTunnel.getExpTime() <= System.currentTimeMillis()) {
                        return R.err("用户的该隧道权限已到期");
                    }

                    // 检查原用户的流量和转发数量限制
                    R quotaCheckResult = checkForwardQuota(existForward.getUserId(), tunnel.getId().intValue(), userTunnel, originalUser, forwardUpdateDto.getId());
                    if (quotaCheckResult.getCode() != 0) {
                        return R.err("用户" + quotaCheckResult.getMsg());
                    }

                    permissionResult = UserPermissionResult.success(userTunnel.getSpeedId(), userTunnel);
                }
            } else {
                // 普通用户检查自己的权限
                permissionResult = checkUserPermissions(currentUser, tunnel, forwardUpdateDto.getId());
                if (permissionResult.isHasError()) {
                    return R.err(permissionResult.getErrorMessage());
                }
            }
        }

        // 5. 获取UserTunnel（即使隧道未变化也需要获取，用于构建服务名称）
        UserTunnel userTunnel = null;
        if (currentUser.getRoleId() != ADMIN_ROLE_ID) {
            userTunnel = getUserTunnel(currentUser.getUserId(), tunnel.getId().intValue());
            if (userTunnel == null) {
                return R.err("你没有该隧道权限");
            }
        } else {
            // 管理员用户也需要获取UserTunnel（如果存在的话），用于构建正确的服务名称
            // 通过forward记录获取原始的用户ID
            userTunnel = getUserTunnel(existForward.getUserId(), tunnel.getId().intValue());
        }

        // 6. 获取所需节点；节点组会先自动剔除离线成员。
        NodeInfo nodeInfo = getRequiredNodes(tunnel);
        if (nodeInfo.isHasError()) {
            return R.err(nodeInfo.getErrorMessage());
        }

        // 7. 更新Forward对象
        Forward updatedForward = updateForwardEntity(forwardUpdateDto, existForward, tunnel);

        // 8. 调用Gost服务更新转发
        R gostResult;
        if (tunnelChanged) {
            // 隧道变化时：先删除原配置，再创建新配置
            gostResult = updateGostServicesWithTunnelChange(existForward, updatedForward, tunnel, permissionResult != null ? permissionResult.getLimiter() : null, nodeInfo, userTunnel);
        } else {
            // 隧道未变化时：直接更新配置
            gostResult = updateGostServices(updatedForward, tunnel, permissionResult != null ? permissionResult.getLimiter() : null, nodeInfo, userTunnel);
        }

        if (gostResult.getCode() != 0) {
            return gostResult;
        }
        updatedForward.setStatus(1);
        // 9. 保存更新
        boolean result = this.updateById(updatedForward);
        return result ? R.ok("端口转发更新成功") : R.err("端口转发更新失败");
    }

    @Override
    public R deleteForward(Long id) {
        // 1. 获取当前用户信息
        UserInfo currentUser = getCurrentUserInfo();

        // 2. 检查转发是否存在
        Forward forward = validateForwardExists(id, currentUser);
        if (forward == null) {
            return R.err("端口转发不存在");
        }

        // 3. 获取隧道信息
        Tunnel tunnel = validateTunnel(forward.getTunnelId());
        if (tunnel == null) {
            return R.err("隧道不存在");
        }

        // 4. 权限检查（仅普通用户需要）
        UserTunnel userTunnel = null;
        if (currentUser.getRoleId() != ADMIN_ROLE_ID) {
            userTunnel = getUserTunnel(currentUser.getUserId(), tunnel.getId().intValue());
            if (userTunnel == null) {
                return R.err("你没有该隧道权限");
            }
        } else {
            // 管理员删除用户记录时，需要获取对应的UserTunnel用于构建正确的服务名称
            userTunnel = getUserTunnel(forward.getUserId(), tunnel.getId().intValue());
        }

        // 5. 获取所需的节点信息
        NodeInfo nodeInfo = getRequiredNodes(tunnel);
        if (nodeInfo.isHasError()) {
            return R.err(nodeInfo.getErrorMessage());
        }

        // 6. 调用Gost服务删除转发
        R gostResult = deleteGostServices(forward, tunnel, nodeInfo, userTunnel);
        if (gostResult.getCode() != 0) {
            return gostResult;
        }

        // 7. 删除转发记录
        boolean result = this.removeById(id);
        if (result) {
            return R.ok("端口转发删除成功");
        } else {
            return R.err("端口转发删除失败");
        }
    }

    @Override
    public R pauseForward(Long id) {
        return changeForwardStatus(id, FORWARD_STATUS_PAUSED, "暂停", "PauseService");
    }

    @Override
    public R resumeForward(Long id) {
        return changeForwardStatus(id, FORWARD_STATUS_ACTIVE, "恢复", "ResumeService");
    }

    @Override
    public R forceDeleteForward(Long id) {
        // 1. 获取当前用户信息
        UserInfo currentUser = getCurrentUserInfo();

        // 2. 检查转发是否存在且用户有权限操作
        Forward forward = validateForwardExists(id, currentUser);
        if (forward == null) {
            return R.err("端口转发不存在");
        }

        // 3. 直接删除转发记录，跳过GOST服务删除
        boolean result = this.removeById(id);
        if (result) {
            return R.ok("端口转发强制删除成功");
        } else {
            return R.err("端口转发强制删除失败");
        }
    }

    @Override
    public R deleteForwards(List<Long> ids, boolean force) {
        if (ids == null || ids.isEmpty()) {
            return R.err("请提供要删除的转发ID列表");
        }
        // 去重，避免同一转发被重复处理
        List<Long> distinctIds = ids.stream().distinct().collect(Collectors.toList());

        List<Long> successIds = new ArrayList<>();
        List<Map<String, Object>> failedList = new ArrayList<>();

        for (Long id : distinctIds) {
            R result = force ? forceDeleteForward(id) : deleteForward(id);
            if (result.getCode() == 0) {
                successIds.add(id);
            } else {
                Map<String, Object> failed = new HashMap<>();
                failed.put("id", id);
                failed.put("reason", result.getMsg());
                failedList.add(failed);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("total", distinctIds.size());
        data.put("successIds", successIds);
        data.put("failed", failedList);

        if (failedList.isEmpty()) {
            return R.ok(data);
        }

        // ha-min: 全部失败时也返回明细结构（而非纯错误），便于前端区分展示失败原因
        R res = R.err("部分或全部转发删除失败");
        res.setData(data);
        return res;
    }
    @Override
    public R syncNodeGroupForwards(Long groupId, List<Long> oldNodeIds, List<Long> newNodeIds) {
        if (groupId == null) {
            return R.err("节点组ID不能为空");
        }
        List<Long> removedNodeIds = removedNodeIds(oldNodeIds, newNodeIds);
        List<Node> removedNodes = resolveNodesByIds(removedNodeIds);
        List<Tunnel> tunnels = tunnelService.list(new QueryWrapper<Tunnel>()
                .and(wrapper -> wrapper.eq("in_group_id", groupId).or().eq("out_group_id", groupId)));

        int total = 0;
        int success = 0;
        List<Map<String, Object>> failed = new ArrayList<>();
        for (Tunnel tunnel : tunnels) {
            List<Forward> forwards = list(new QueryWrapper<Forward>()
                    .eq("tunnel_id", tunnel.getId())
                    .eq("status", FORWARD_STATUS_ACTIVE));
            for (Forward forward : forwards) {
                total++;
                R result = syncForwardAfterGroupChange(groupId, removedNodes, tunnel, forward);
                if (result.getCode() == 0) {
                    success++;
                } else {
                    Map<String, Object> item = new HashMap<>();
                    item.put("forwardId", forward.getId());
                    item.put("forwardName", forward.getName());
                    item.put("tunnelId", tunnel.getId());
                    item.put("reason", result.getMsg());
                    failed.add(item);
                }
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("success", success);
        data.put("failed", failed);
        if (failed.isEmpty()) {
            return R.ok(data);
        }
        R res = R.err("部分转发同步失败");
        res.setData(data);
        return res;
    }

    private R syncForwardAfterGroupChange(Long groupId, List<Node> removedNodes, Tunnel tunnel, Forward forward) {
        UserTunnel userTunnel = getUserTunnel(forward.getUserId(), tunnel.getId().intValue());
        R deleteResult = deleteRemovedGroupNodeServices(groupId, removedNodes, tunnel, forward, userTunnel);
        if (deleteResult.getCode() != 0) {
            return deleteResult;
        }
        NodeInfo nodeInfo = getRequiredNodes(tunnel);
        if (nodeInfo.isHasError()) {
            return R.err(nodeInfo.getErrorMessage());
        }
        Integer limiter = userTunnel == null ? null : userTunnel.getSpeedId();
        return updateGostServices(forward, tunnel, limiter, nodeInfo, userTunnel);
    }

    private R deleteRemovedGroupNodeServices(Long groupId, List<Node> removedNodes, Tunnel tunnel, Forward forward, UserTunnel userTunnel) {
        if (removedNodes.isEmpty()) {
            return R.ok();
        }
        String serviceName = buildServiceName(forward.getId(), forward.getUserId(), userTunnel);
        boolean entryGroupChanged = Objects.equals(tunnel.getInGroupId(), groupId);
        boolean outGroupChanged = Objects.equals(tunnel.getOutGroupId(), groupId);
        if (entryGroupChanged) {
            for (Node removedNode : removedNodes) {
                if (removedNode.getStatus() != NODE_STATUS_ONLINE) {
                    continue;
                }
                GostDto serviceResult = GostUtil.DeleteService(removedNode.getId(), serviceName);
                if (!isGostOperationSuccess(serviceResult) && !isGostNotFound(serviceResult)) {
                    return R.err(serviceResult.getMsg());
                }
                if (tunnel.getType() == TUNNEL_TYPE_TUNNEL_FORWARD) {
                    GostDto chainResult = GostUtil.DeleteChains(removedNode.getId(), serviceName);
                    if (!isGostOperationSuccess(chainResult) && !isGostNotFound(chainResult)) {
                        return R.err(chainResult.getMsg());
                    }
                }
            }
        }
        if (outGroupChanged && tunnel.getType() == TUNNEL_TYPE_TUNNEL_FORWARD) {
            for (Node removedNode : removedNodes) {
                if (removedNode.getStatus() != NODE_STATUS_ONLINE) {
                    continue;
                }
                GostDto remoteResult = GostUtil.DeleteRemoteService(removedNode.getId(), serviceName);
                if (!isGostOperationSuccess(remoteResult) && !isGostNotFound(remoteResult)) {
                    return R.err(remoteResult.getMsg());
                }
            }
        }
        return R.ok();
    }

    private List<Long> removedNodeIds(List<Long> oldNodeIds, List<Long> newNodeIds) {
        Set<Long> newSet = new HashSet<>(newNodeIds == null ? Collections.emptyList() : newNodeIds);
        List<Long> removed = new ArrayList<>();
        if (oldNodeIds != null) {
            for (Long nodeId : oldNodeIds) {
                if (nodeId != null && !newSet.contains(nodeId)) {
                    removed.add(nodeId);
                }
            }
        }
        return removed;
    }

    private List<Node> resolveNodesByIds(List<Long> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Node> nodeMap = nodeService.listByIds(nodeIds).stream()
                .collect(Collectors.toMap(Node::getId, node -> node, (first, ignored) -> first));
        List<Node> nodes = new ArrayList<>();
        for (Long nodeId : nodeIds) {
            Node node = nodeMap.get(nodeId);
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }


    /**
     * 改变转发状态（暂停/恢复）
     */
    private R changeForwardStatus(Long id, int targetStatus, String operation, String gostMethod) {
        // 1. 获取当前用户信息
        UserInfo currentUser = getCurrentUserInfo();

        if (currentUser.getRoleId() != ADMIN_ROLE_ID) {
            User user = userService.getById(currentUser.getUserId());
            if (user == null) return R.err("用户不存在");
            if (user.getStatus() == 0) return R.err("用户已到期或被禁用");
        }


        // 2. 检查转发是否存在
        Forward forward = validateForwardExists(id, currentUser);
        if (forward == null) {
            return R.err("转发不存在");
        }

        // 3. 获取隧道信息
        Tunnel tunnel = validateTunnel(forward.getTunnelId());
        if (tunnel == null) {
            return R.err("隧道不存在");
        }

        // 4. 恢复服务时需要额外检查
        UserTunnel userTunnel = null;
        if (targetStatus == FORWARD_STATUS_ACTIVE) {
            if (tunnel.getStatus() != TUNNEL_STATUS_ACTIVE) {
                return R.err("隧道已禁用，无法恢复服务");
            }

            // 普通用户需要检查流量和账户状态
            if (currentUser.getRoleId() != ADMIN_ROLE_ID) {
                R flowCheckResult = checkUserFlowLimits(currentUser.getUserId(), tunnel);
                if (flowCheckResult.getCode() != 0) {
                    return flowCheckResult;
                }

                userTunnel = getUserTunnel(currentUser.getUserId(), tunnel.getId().intValue());
                if (userTunnel == null) {
                    return R.err("你没有该隧道权限");
                }

                if (userTunnel.getStatus() != 1) {
                    return R.err("隧道被禁用");
                }
            }
        }

        // 5. 权限检查（仅普通用户需要）
        if (currentUser.getRoleId() != ADMIN_ROLE_ID && userTunnel == null) {
            userTunnel = getUserTunnel(currentUser.getUserId(), tunnel.getId().intValue());
            if (userTunnel == null) {
                return R.err("你没有该隧道权限");
            }
        }

        // 6. 确保获取UserTunnel用于构建服务名称（包括管理员用户）
        if (userTunnel == null) {
            // 通过forward记录获取原始的用户ID来查找UserTunnel
            userTunnel = getUserTunnel(forward.getUserId(), tunnel.getId().intValue());
        }

        // 7. 获取所需的节点信息
        NodeInfo nodeInfo = getRequiredNodes(tunnel);
        if (nodeInfo.isHasError()) {
            return R.err(nodeInfo.getErrorMessage());
        }

        // 8. 调用Gost服务
        String serviceName = buildServiceName(forward.getId(), forward.getUserId(), userTunnel);
        R statusResult = changeGostServiceStatus(nodeInfo, tunnel, serviceName, operation, gostMethod);
        if (statusResult.getCode() != 0) {
            return statusResult;
        }

        // 9. 更新转发状态
        forward.setStatus(targetStatus);
        forward.setUpdatedTime(System.currentTimeMillis());
        boolean result = this.updateById(forward);

        return result ? R.ok("服务已" + operation) : R.err("更新状态失败");
    }

    private R changeGostServiceStatus(NodeInfo nodeInfo, Tunnel tunnel, String serviceName, String operation, String gostMethod) {
        boolean pause = "PauseService".equals(gostMethod);
        for (Node inNode : nodeInfo.getInNodes()) {
            GostDto result = pause ? GostUtil.PauseService(inNode.getId(), serviceName) : GostUtil.ResumeService(inNode.getId(), serviceName);
            if (!isGostOperationSuccess(result)) {
                return R.err(operation + "入口服务失败：" + result.getMsg());
            }
        }
        if (tunnel.getType() == TUNNEL_TYPE_TUNNEL_FORWARD) {
            for (Node outNode : nodeInfo.getOutNodes()) {
                GostDto result = pause ? GostUtil.PauseRemoteService(outNode.getId(), serviceName) : GostUtil.ResumeRemoteService(outNode.getId(), serviceName);
                if (!isGostOperationSuccess(result)) {
                    return R.err(operation + "远端服务失败：" + result.getMsg());
                }
            }
        }
        return R.ok();
    }

    @Override
    public R diagnoseForward(Long id) {
        // 1. 获取当前用户信息
        UserInfo currentUser = getCurrentUserInfo();

        // 2. 检查转发是否存在且用户有权限访问
        Forward forward = validateForwardExists(id, currentUser);
        if (forward == null) {
            return R.err("转发不存在");
        }

        // 3. 获取隧道信息
        Tunnel tunnel = validateTunnel(forward.getTunnelId());
        if (tunnel == null) {
            return R.err("隧道不存在");
        }

        // 4. 获取节点组展开后的入口/出口节点信息
        NodeInfo nodeInfo = getRequiredNodes(tunnel);
        if (nodeInfo.isHasError()) {
            return R.err(nodeInfo.getErrorMessage());
        }

        List<DiagnosisResult> results = new ArrayList<>();
        String[] remoteAddresses = forward.getRemoteAddr().split(",");
        // 6. 根据隧道类型执行不同的诊断策略
        if (tunnel.getType() == TUNNEL_TYPE_PORT_FORWARD) {
            // 端口转发：每个入口节点都应能直连目标地址。
            for (Node inNode : nodeInfo.getInNodes()) {
                for (String remoteAddress : remoteAddresses) {
                    String targetIp = extractIpFromAddress(remoteAddress);
                    int targetPort = extractPortFromAddress(remoteAddress);
                    if (targetIp == null || targetPort == -1) {
                        return R.err("无法解析目标地址: " + remoteAddress);
                    }

                    DiagnosisResult result = performTcpPingDiagnosis(inNode, targetIp, targetPort, "转发->目标");
                    results.add(result);
                }
            }
        } else {
            // 隧道转发：每个入口检查到每个出口，每个出口检查目标。
            for (Node inNode : nodeInfo.getInNodes()) {
                for (Node outNode : nodeInfo.getOutNodes()) {
                    DiagnosisResult inToOutResult = performTcpPingDiagnosis(inNode, firstNodeAddress(outNode), forward.getOutPort(), "入口->出口");
                    results.add(inToOutResult);
                }
            }

            for (Node outNode : nodeInfo.getOutNodes()) {
                for (String remoteAddress : remoteAddresses) {
                    String targetIp = extractIpFromAddress(remoteAddress);
                    int targetPort = extractPortFromAddress(remoteAddress);
                    if (targetIp == null || targetPort == -1) {
                        return R.err("无法解析目标地址: " + remoteAddress);
                    }
                    DiagnosisResult outToTargetResult = performTcpPingDiagnosis(outNode, targetIp, targetPort, "出口->目标");
                    results.add(outToTargetResult);
                }
            }
        }

        // 7. 构建诊断报告
        Map<String, Object> diagnosisReport = new HashMap<>();
        diagnosisReport.put("forwardId", id);
        diagnosisReport.put("forwardName", forward.getName());
        diagnosisReport.put("tunnelType", tunnel.getType() == TUNNEL_TYPE_PORT_FORWARD ? "端口转发" : "隧道转发");
        diagnosisReport.put("results", results);
        diagnosisReport.put("timestamp", System.currentTimeMillis());

        return R.ok(diagnosisReport);
    }

    @Override
    public R updateForwardOrder(Map<String, Object> params) {
        try {
            // 1. 获取当前用户信息
            UserInfo currentUser = getCurrentUserInfo();

            // 2. 验证参数
            if (!params.containsKey("forwards")) {
                return R.err("缺少forwards参数");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> forwardsList = (List<Map<String, Object>>) params.get("forwards");
            if (forwardsList == null || forwardsList.isEmpty()) {
                return R.err("forwards参数不能为空");
            }

            // 3. 验证用户权限（只能更新自己的转发）
            if (currentUser.getRoleId() != ADMIN_ROLE_ID) {
                // 普通用户只能更新自己的转发
                List<Long> forwardIds = forwardsList.stream()
                        .map(item -> Long.valueOf(item.get("id").toString()))
                        .collect(Collectors.toList());

                // 检查所有转发是否属于当前用户
                QueryWrapper<Forward> queryWrapper = new QueryWrapper<>();
                queryWrapper.in("id", forwardIds);
                queryWrapper.eq("user_id", currentUser.getUserId());

                long count = this.count(queryWrapper);
                if (count != forwardIds.size()) {
                    return R.err("只能更新自己的转发排序");
                }
            }

            // 4. 批量更新排序
            List<Forward> forwardsToUpdate = new ArrayList<>();
            for (Map<String, Object> forwardData : forwardsList) {
                Long id = Long.valueOf(forwardData.get("id").toString());
                Integer inx = Integer.valueOf(forwardData.get("inx").toString());

                Forward forward = new Forward();
                forward.setId(id);
                forward.setInx(inx);
                forwardsToUpdate.add(forward);
            }

            // 5. 执行批量更新
            boolean success = this.updateBatchById(forwardsToUpdate);
            if (success) {
                log.info("用户 {} 更新了 {} 个转发的排序", currentUser.getUserName(), forwardsToUpdate.size());
                return R.ok("排序更新成功");
            } else {
                return R.err("排序更新失败");
            }

        } catch (Exception e) {
            log.error("更新转发排序失败", e);
            return R.err("更新排序时发生错误: " + e.getMessage());
        }
    }

    /**
     * 从地址字符串中提取IP地址
     * 支持格式: ip:port, [ipv6]:port, domain:port
     */
    private String extractIpFromAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return null;
        }

        address = address.trim();

        // IPv6格式: [ipv6]:port
        if (address.startsWith("[")) {
            int closeBracket = address.indexOf(']');
            if (closeBracket > 1) {
                return address.substring(1, closeBracket);
            }
        }

        // IPv4或域名格式: ip:port 或 domain:port
        int lastColon = address.lastIndexOf(':');
        if (lastColon > 0) {
            return address.substring(0, lastColon);
        }

        // 如果没有端口，直接返回地址
        return address;
    }

    /**
     * 从地址字符串中提取端口号
     * 支持格式: ip:port, [ipv6]:port, domain:port
     */
    private int extractPortFromAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return -1;
        }

        address = address.trim();

        // IPv6格式: [ipv6]:port
        if (address.startsWith("[")) {
            int closeBracket = address.indexOf(']');
            if (closeBracket > 1 && closeBracket + 1 < address.length() && address.charAt(closeBracket + 1) == ':') {
                String portStr = address.substring(closeBracket + 2);
                try {
                    return Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }

        // IPv4或域名格式: ip:port 或 domain:port
        int lastColon = address.lastIndexOf(':');
        if (lastColon > 0 && lastColon + 1 < address.length()) {
            String portStr = address.substring(lastColon + 1);
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        // 如果没有端口，返回-1表示无法解析
        return -1;
    }

    /**
     * 执行TCP ping诊断
     *
     * @param node        执行TCP ping的节点
     * @param targetIp    目标IP地址
     * @param port        目标端口
     * @param description 诊断描述
     * @return 诊断结果
     */
    private DiagnosisResult performTcpPingDiagnosis(Node node, String targetIp, int port, String description) {
        try {
            // 构建TCP ping请求数据
            JSONObject tcpPingData = new JSONObject();
            tcpPingData.put("ip", targetIp);
            tcpPingData.put("port", port);
            tcpPingData.put("count", 2);
            tcpPingData.put("timeout", 3000); // 5秒超时

            // 发送TCP ping命令到节点
            GostDto gostResult = WebSocketServer.send_msg(node.getId(), tcpPingData, "TcpPing");

            DiagnosisResult result = new DiagnosisResult();
            result.setNodeId(node.getId());
            result.setNodeName(node.getName());
            result.setTargetIp(targetIp);
            result.setTargetPort(port);
            result.setDescription(description);
            result.setTimestamp(System.currentTimeMillis());

            if (gostResult != null && "OK".equals(gostResult.getMsg())) {
                // 尝试解析TCP ping响应数据
                try {
                    if (gostResult.getData() != null) {
                        JSONObject tcpPingResponse = (JSONObject) gostResult.getData();
                        boolean success = tcpPingResponse.getBooleanValue("success");

                        result.setSuccess(success);
                        if (success) {
                            result.setMessage("TCP连接成功");
                            result.setAverageTime(tcpPingResponse.getDoubleValue("averageTime"));
                            result.setPacketLoss(tcpPingResponse.getDoubleValue("packetLoss"));
                        } else {
                            result.setMessage(tcpPingResponse.getString("errorMessage"));
                            result.setAverageTime(-1.0);
                            result.setPacketLoss(100.0);
                        }
                    } else {
                        // 没有详细数据，使用默认值
                        result.setSuccess(true);
                        result.setMessage("TCP连接成功");
                        result.setAverageTime(0.0);
                        result.setPacketLoss(0.0);
                    }
                } catch (Exception e) {
                    // 解析响应数据失败，但TCP ping命令本身成功了
                    result.setSuccess(true);
                    result.setMessage("TCP连接成功，但无法解析详细数据");
                    result.setAverageTime(0.0);
                    result.setPacketLoss(0.0);
                }
            } else {
                result.setSuccess(false);
                result.setMessage(gostResult != null ? gostResult.getMsg() : "节点无响应");
                result.setAverageTime(-1.0);
                result.setPacketLoss(100.0);
            }

            return result;
        } catch (Exception e) {
            DiagnosisResult result = new DiagnosisResult();
            result.setNodeId(node.getId());
            result.setNodeName(node.getName());
            result.setTargetIp(targetIp);
            result.setTargetPort(port);
            result.setDescription(description);
            result.setSuccess(false);
            result.setMessage("诊断执行异常: " + e.getMessage());
            result.setTimestamp(System.currentTimeMillis());
            result.setAverageTime(-1.0);
            result.setPacketLoss(100.0);
            return result;
        }
    }

    /**
     * 获取当前用户信息
     */
    private UserInfo getCurrentUserInfo() {
        Integer userId = JwtUtil.getUserIdFromToken();
        Integer roleId = JwtUtil.getRoleIdFromToken();
        String userName = JwtUtil.getNameFromToken();
        return new UserInfo(userId, roleId, userName);
    }

    /**
     * 验证隧道是否存在
     */
    private Tunnel validateTunnel(Integer tunnelId) {
        return tunnelService.getById(tunnelId);
    }

    /**
     * 验证转发是否存在且用户有权限访问
     */
    private Forward validateForwardExists(Long forwardId, UserInfo currentUser) {
        Forward forward = this.getById(forwardId);
        if (forward == null) {
            return null;
        }

        // 普通用户只能操作自己的转发
        if (currentUser.getRoleId() != ADMIN_ROLE_ID &&
                !Objects.equals(currentUser.getUserId(), forward.getUserId())) {
            return null;
        }

        return forward;
    }

    /**
     * 获取所需的节点信息
     */
    private NodeInfo getRequiredNodes(Tunnel tunnel) {
        List<Node> inNodes = resolveTunnelNodes(tunnel.getInGroupId(), tunnel.getInNodeId());
        if (inNodes.isEmpty()) {
            return NodeInfo.error(tunnel.getInGroupId() == null ? "入口节点不存在" : "入口节点组没有在线节点");
        }
        if (tunnel.getInGroupId() == null) {
            for (Node node : inNodes) {
                if (node.getStatus() != NODE_STATUS_ONLINE) {
                    return NodeInfo.error("入口节点离线: " + node.getName());
                }
            }
        }

        List<Node> outNodes = Collections.emptyList();
        if (tunnel.getType() == TUNNEL_TYPE_TUNNEL_FORWARD) {
            outNodes = resolveTunnelNodes(tunnel.getOutGroupId(), tunnel.getOutNodeId());
            if (outNodes.isEmpty()) {
                return NodeInfo.error(tunnel.getOutGroupId() == null ? "出口节点不存在" : "出口节点组没有在线节点");
            }
            if (tunnel.getOutGroupId() == null) {
                for (Node node : outNodes) {
                    if (node.getStatus() != NODE_STATUS_ONLINE) {
                        return NodeInfo.error("出口节点离线: " + node.getName());
                    }
                }
            }
        }

        return NodeInfo.success(inNodes, outNodes);
    }

    private List<Node> resolveTunnelNodes(Long groupId, Long nodeId) {
        if (groupId != null) {
            R pruneResult = aggregateNodeGroupService.pruneOfflineNodes(groupId);
            if (pruneResult.getCode() != 0) {
                log.warn("自动清理节点组{}离线成员失败: {}", groupId, pruneResult.getMsg());
            }
            AggregateNodeGroup group = aggregateNodeGroupService.getById(groupId);
            if (group == null) {
                return Collections.emptyList();
            }
            List<Long> nodeIds = aggregateNodeGroupService.parseNodeIds(group);
            if (nodeIds.isEmpty()) {
                return Collections.emptyList();
            }
            Map<Long, Node> nodeMap = nodeService.listByIds(nodeIds).stream().collect(Collectors.toMap(Node::getId, node -> node));
            List<Node> nodes = new ArrayList<>();
            for (Long id : nodeIds) {
                Node node = nodeMap.get(id);
                if (node != null && node.getStatus() == NODE_STATUS_ONLINE) {
                    nodes.add(node);
                }
            }
            return nodes;
        }
        Node node = nodeService.getNodeById(nodeId);
        return node == null ? Collections.emptyList() : Collections.singletonList(node);
    }

    /**
     * 检查用户权限和限制
     */
    private UserPermissionResult checkUserPermissions(UserInfo currentUser, Tunnel tunnel, Long excludeForwardId) {
        if (currentUser.getRoleId() == ADMIN_ROLE_ID) {
            return UserPermissionResult.success(null, null);
        }

        // 获取用户信息
        User userInfo = userService.getById(currentUser.getUserId());
        if (userInfo.getExpTime() != null && userInfo.getExpTime() <= System.currentTimeMillis()) {
            return UserPermissionResult.error("当前账号已到期");
        }

        // 检查用户隧道权限
        UserTunnel userTunnel = getUserTunnel(currentUser.getUserId(), tunnel.getId().intValue());
        if (userTunnel == null) {
            return UserPermissionResult.error("你没有该隧道权限");
        }

        if (userTunnel.getStatus() != 1) {
            return UserPermissionResult.error("隧道被禁用");
        }

        // 检查隧道权限到期时间
        if (userTunnel.getExpTime() != null && userTunnel.getExpTime() <= System.currentTimeMillis()) {
            return UserPermissionResult.error("该隧道权限已到期");
        }

        // 流量限制检查
        if (userInfo.getFlow() <= 0) {
            return UserPermissionResult.error("用户总流量已用完");
        }
        if (userTunnel.getFlow() <= 0) {
            return UserPermissionResult.error("该隧道流量已用完");
        }

        // 转发数量限制检查
        R quotaCheckResult = checkForwardQuota(currentUser.getUserId(), tunnel.getId().intValue(), userTunnel, userInfo, excludeForwardId);
        if (quotaCheckResult.getCode() != 0) {
            return UserPermissionResult.error(quotaCheckResult.getMsg());
        }

        return UserPermissionResult.success(userTunnel.getSpeedId(), userTunnel);
    }

    /**
     * 检查用户转发数量限制
     */
    private R checkForwardQuota(Integer userId, Integer tunnelId, UserTunnel userTunnel, User userInfo, Long excludeForwardId) {
        // 检查用户总转发数量限制
        long userForwardCount = this.count(new QueryWrapper<Forward>().eq("user_id", userId));
        if (userForwardCount >= userInfo.getNum()) {
            return R.err("用户总转发数量已达上限，当前限制：" + userInfo.getNum() + "个");
        }

        // 检查用户在该隧道的转发数量限制
        QueryWrapper<Forward> tunnelQuery = new QueryWrapper<Forward>()
                .eq("user_id", userId)
                .eq("tunnel_id", tunnelId);

        if (excludeForwardId != null) {
            tunnelQuery.ne("id", excludeForwardId);
        }

        long tunnelForwardCount = this.count(tunnelQuery);
        if (tunnelForwardCount >= userTunnel.getNum()) {
            return R.err("该隧道转发数量已达上限，当前限制：" + userTunnel.getNum() + "个");
        }

        return R.ok();
    }

    /**
     * 检查用户流量限制
     */
    private R checkUserFlowLimits(Integer userId, Tunnel tunnel) {
        User userInfo = userService.getById(userId);
        if (userInfo.getExpTime() != null && userInfo.getExpTime() <= System.currentTimeMillis()) {
            return R.err("当前账号已到期");
        }

        UserTunnel userTunnel = getUserTunnel(userId, tunnel.getId().intValue());
        if (userTunnel == null) {
            return R.err("你没有该隧道权限");
        }

        // 检查隧道权限到期时间
        if (userTunnel.getExpTime() != null && userTunnel.getExpTime() <= System.currentTimeMillis()) {
            return R.err("该隧道权限已到期，无法恢复服务");
        }

        // 检查用户总流量限制
        if (userInfo.getFlow() * BYTES_TO_GB <= userInfo.getInFlow() + userInfo.getOutFlow()) {
            return R.err("用户总流量已用完，无法恢复服务");
        }

        // 检查隧道流量限制
        // 数据库中的流量已按计费类型处理，直接使用总和
        long tunnelFlow = userTunnel.getInFlow() + userTunnel.getOutFlow();

        if (userTunnel.getFlow() * BYTES_TO_GB <= tunnelFlow) {
            return R.err("该隧道流量已用完，无法恢复服务");
        }

        return R.ok();
    }

    /**
     * 分配端口
     */
    private PortAllocation allocatePorts(Tunnel tunnel, Integer specifiedInPort) {
        return allocatePorts(tunnel, specifiedInPort, null);
    }

    /**
     * 分配端口
     */
    private PortAllocation allocatePorts(Tunnel tunnel, Integer specifiedInPort, Long excludeForwardId) {
        Integer inPort;

        if (specifiedInPort != null) {
            // 用户指定了入口端口，需要检查是否可用
            if (!isInPortAvailable(tunnel, specifiedInPort, excludeForwardId)) {
                return PortAllocation.error("指定的入口端口 " + specifiedInPort + " 已被占用或不在允许范围内");
            }
            inPort = specifiedInPort;
        } else {
            // 用户未指定端口时自动分配
            inPort = allocateInPort(tunnel, excludeForwardId);
            if (inPort == null) {
                return PortAllocation.error("隧道入口端口已满，无法分配新端口");
            }
        }

        Integer outPort = null;
        if (tunnel.getType() == TUNNEL_TYPE_TUNNEL_FORWARD) {
            outPort = allocateOutPort(tunnel, excludeForwardId);
            if (outPort == null) {
                return PortAllocation.error("隧道出口端口已满，无法分配新端口");
            }
        }

        return PortAllocation.success(inPort, outPort);
    }

    /**
     * 创建Forward实体对象
     */
    private Forward createForwardEntity(ForwardDto forwardDto, Tunnel tunnel, UserInfo currentUser, PortAllocation portAllocation) {
        Forward forward = new Forward();
        // 先复制DTO的属性，再设置其他属性，避免被覆盖
        BeanUtils.copyProperties(forwardDto, forward);
        forward.setStatus(FORWARD_STATUS_ACTIVE);
        forward.setInPort(portAllocation.getInPort());
        forward.setOutPort(portAllocation.getOutPort());
        forward.setStrategy(tunnelStrategy(tunnel));
        forward.setUserId(currentUser.getUserId());
        forward.setUserName(currentUser.getUserName());
        forward.setCreatedTime(System.currentTimeMillis());
        forward.setUpdatedTime(System.currentTimeMillis());
        return forward;
    }

    /**
     * 更新Forward实体对象
     */
    private Forward updateForwardEntity(ForwardUpdateDto forwardUpdateDto, Forward existForward, Tunnel tunnel) {
        Forward forward = new Forward();
        BeanUtils.copyProperties(forwardUpdateDto, forward);

        // 处理端口分配逻辑
        boolean tunnelChanged = !existForward.getTunnelId().equals(forwardUpdateDto.getTunnelId());
        boolean inPortChanged = forwardUpdateDto.getInPort() != null &&
                !Objects.equals(forwardUpdateDto.getInPort(), existForward.getInPort());

        if (tunnelChanged || inPortChanged) {
            // 隧道变化或入口端口变化时需要重新分配
            Integer specifiedInPort = forwardUpdateDto.getInPort();
            // 如果没有指定新端口但隧道未变化，保持原端口
            if (specifiedInPort == null && !tunnelChanged) {
                specifiedInPort = existForward.getInPort();
            }

            PortAllocation portAllocation = allocatePorts(tunnel, specifiedInPort, forwardUpdateDto.getId());
            if (portAllocation.isHasError()) {
                throw new RuntimeException(portAllocation.getErrorMessage());
            }
            forward.setInPort(portAllocation.getInPort());
            forward.setOutPort(portAllocation.getOutPort());
        } else {
            // 隧道和端口都未变化，保持原端口
            forward.setInPort(existForward.getInPort());
            forward.setOutPort(existForward.getOutPort());
        }

        forward.setStrategy(tunnelStrategy(tunnel));
        forward.setUpdatedTime(System.currentTimeMillis());
        return forward;
    }

    /**
     * 创建Gost服务
     */
    private R createGostServices(Forward forward, Tunnel tunnel, Integer limiter, NodeInfo nodeInfo, UserTunnel userTunnel) {
        String serviceName = buildServiceName(forward.getId(), forward.getUserId(), userTunnel);
        String interfaceName = tunnel.getType() == TUNNEL_TYPE_TUNNEL_FORWARD ? null : forward.getInterfaceName();
        String strategy = tunnelStrategy(tunnel);

        if (tunnel.getType() == TUNNEL_TYPE_TUNNEL_FORWARD) {
            String chainRemoteAddr = buildNodeRemoteAddr(nodeInfo.getOutNodes(), forward.getOutPort());
            for (Node inNode : nodeInfo.getInNodes()) {
                R chainResult = createChainService(inNode, serviceName, chainRemoteAddr, tunnel.getProtocol(), tunnel.getInterfaceName(), strategy);
                if (chainResult.getCode() != 0) {
                    cleanupGostServices(serviceName, nodeInfo, tunnel.getType());
                    return chainResult;
                }
            }
            for (Node outNode : nodeInfo.getOutNodes()) {
                R remoteResult = createRemoteService(outNode, serviceName, forward, tunnel.getProtocol(), forward.getInterfaceName(), strategy);
                if (remoteResult.getCode() != 0) {
                    cleanupGostServices(serviceName, nodeInfo, tunnel.getType());
                    return remoteResult;
                }
            }
        }

        for (Node inNode : nodeInfo.getInNodes()) {
            R serviceResult = createMainService(inNode, serviceName, forward, limiter, tunnel.getType(), tunnel, strategy, interfaceName);
            if (serviceResult.getCode() != 0) {
                cleanupGostServices(serviceName, nodeInfo, tunnel.getType());
                return serviceResult;
            }
        }
        return R.ok();
    }

    /**
     * 更新Gost服务
     */
    private R updateGostServices(Forward forward, Tunnel tunnel, Integer limiter, NodeInfo nodeInfo, UserTunnel userTunnel) {
        String serviceName = buildServiceName(forward.getId(), forward.getUserId(), userTunnel);
        String interfaceName = tunnel.getType() == TUNNEL_TYPE_TUNNEL_FORWARD ? null : forward.getInterfaceName();
        String strategy = tunnelStrategy(tunnel);

        if (tunnel.getType() == TUNNEL_TYPE_TUNNEL_FORWARD) {
            String chainRemoteAddr = buildNodeRemoteAddr(nodeInfo.getOutNodes(), forward.getOutPort());
            for (Node inNode : nodeInfo.getInNodes()) {
                R chainResult = updateChainService(inNode, serviceName, chainRemoteAddr, tunnel.getProtocol(), tunnel.getInterfaceName(), strategy);
                if (chainResult.getCode() != 0) {
                    updateForwardStatusToError(forward);
                    return chainResult;
                }
            }
            for (Node outNode : nodeInfo.getOutNodes()) {
                R remoteResult = updateRemoteService(outNode, serviceName, forward, tunnel.getProtocol(), forward.getInterfaceName(), strategy);
                if (remoteResult.getCode() != 0) {
                    updateForwardStatusToError(forward);
                    return remoteResult;
                }
            }
        }
        for (Node inNode : nodeInfo.getInNodes()) {
            R serviceResult = updateMainService(inNode, serviceName, forward, limiter, tunnel.getType(), tunnel, strategy, interfaceName);
            if (serviceResult.getCode() != 0) {
                updateForwardStatusToError(forward);
                return serviceResult;
            }
        }
        return R.ok();
    }

    /**
     * 隧道变化时更新Gost服务：先删除原配置，再创建新配置
     */
    private R updateGostServicesWithTunnelChange(Forward existForward, Forward updatedForward, Tunnel newTunnel, Integer limiter, NodeInfo nodeInfo, UserTunnel userTunnel) {
        // 1. 获取原隧道信息
        Tunnel oldTunnel = tunnelService.getById(existForward.getTunnelId());
        if (oldTunnel == null) {
            return R.err("原隧道不存在，无法删除旧配置");
        }

        // 2. 删除原有的Gost服务配置
        R deleteResult = deleteOldGostServices(existForward, oldTunnel);
        if (deleteResult.getCode() != 0) {
            // 删除失败时记录日志，但不影响后续创建（可能原配置已不存在）
            log.info("删除原隧道{}的Gost配置失败: {}", oldTunnel.getId(), deleteResult.getMsg());
        }

        // 3. 创建新的Gost服务配置
        R createResult = createGostServices(updatedForward, newTunnel, limiter, nodeInfo, userTunnel);
        if (createResult.getCode() != 0) {
            updateForwardStatusToError(updatedForward);
            return R.err("创建新隧道配置失败: " + createResult.getMsg());
        }

        return R.ok();
    }

    /**
     * 删除原有的Gost服务（隧道变化时专用）
     */
    private R deleteOldGostServices(Forward forward, Tunnel oldTunnel) {
        UserTunnel oldUserTunnel = getUserTunnel(forward.getUserId(), oldTunnel.getId().intValue());
        NodeInfo oldNodeInfo = getRequiredNodes(oldTunnel);
        if (oldNodeInfo.isHasError()) {
            log.info("原隧道{}节点信息不可用: {}", oldTunnel.getId(), oldNodeInfo.getErrorMessage());
            return R.ok();
        }
        return deleteGostServices(forward, oldTunnel, oldNodeInfo, oldUserTunnel);
    }

    /**
     * 删除Gost服务
     */
    private R deleteGostServices(Forward forward, Tunnel tunnel, NodeInfo nodeInfo, UserTunnel userTunnel) {
        String serviceName = buildServiceName(forward.getId(), forward.getUserId(), userTunnel);
        for (Node inNode : nodeInfo.getInNodes()) {
            GostDto serviceResult = GostUtil.DeleteService(inNode.getId(), serviceName);
            if (!isGostOperationSuccess(serviceResult) && !isGostNotFound(serviceResult)) {
                return R.err(serviceResult.getMsg());
            }
        }
        if (tunnel.getType() == TUNNEL_TYPE_TUNNEL_FORWARD) {
            for (Node inNode : nodeInfo.getInNodes()) {
                GostDto chainResult = GostUtil.DeleteChains(inNode.getId(), serviceName);
                if (!isGostOperationSuccess(chainResult) && !isGostNotFound(chainResult)) {
                    return R.err(chainResult.getMsg());
                }
            }
            for (Node outNode : nodeInfo.getOutNodes()) {
                GostDto remoteResult = GostUtil.DeleteRemoteService(outNode.getId(), serviceName);
                if (!isGostOperationSuccess(remoteResult) && !isGostNotFound(remoteResult)) {
                    return R.err(remoteResult.getMsg());
                }
            }
        }
        return R.ok();
    }

    private void cleanupGostServices(String serviceName, NodeInfo nodeInfo, Integer tunnelType) {
        for (Node inNode : nodeInfo.getInNodes()) {
            GostUtil.DeleteService(inNode.getId(), serviceName);
            if (tunnelType == TUNNEL_TYPE_TUNNEL_FORWARD) {
                GostUtil.DeleteChains(inNode.getId(), serviceName);
            }
        }
        if (tunnelType == TUNNEL_TYPE_TUNNEL_FORWARD) {
            for (Node outNode : nodeInfo.getOutNodes()) {
                GostUtil.DeleteRemoteService(outNode.getId(), serviceName);
            }
        }
    }

    /**
     * 创建链服务
     */
    private R createChainService(Node inNode, String serviceName, String remoteAddr, String protocol, String interfaceName, String strategy) {
        GostDto result = GostUtil.AddChains(inNode.getId(), serviceName, remoteAddr, protocol, interfaceName, strategy);
        return isGostOperationSuccess(result) ? R.ok() : R.err(result.getMsg());
    }

    /**
     * 创建远程服务
     */
    private R createRemoteService(Node outNode, String serviceName, Forward forward, String protocol, String interfaceName, String strategy) {
        GostDto result = GostUtil.AddRemoteService(outNode.getId(), serviceName, forward.getOutPort(), forward.getRemoteAddr(), protocol, strategy, interfaceName);
        return isGostOperationSuccess(result) ? R.ok() : R.err(result.getMsg());
    }

    /**
     * 创建主服务
     */
    private R createMainService(Node inNode, String serviceName, Forward forward, Integer limiter, Integer tunnelType, Tunnel tunnel, String strategy, String interfaceName) {
        GostDto result = GostUtil.AddService(inNode.getId(), serviceName, forward.getInPort(), limiter, forward.getRemoteAddr(), tunnelType, tunnel, strategy, interfaceName);
        return isGostOperationSuccess(result) ? R.ok() : R.err(result.getMsg());
    }

    /**
     * 更新链服务
     */
    private R updateChainService(Node inNode, String serviceName, String remoteAddr, String protocol, String interfaceName, String strategy) {
        GostDto createResult = GostUtil.UpdateChains(inNode.getId(), serviceName, remoteAddr, protocol, interfaceName, strategy);
        if (isGostNotFound(createResult)) {
            createResult = GostUtil.AddChains(inNode.getId(), serviceName, remoteAddr, protocol, interfaceName, strategy);
        }
        return isGostOperationSuccess(createResult) ? R.ok() : R.err(createResult.getMsg());
    }

    /**
     * 更新远程服务
     */
    private R updateRemoteService(Node outNode, String serviceName, Forward forward, String protocol, String interfaceName, String strategy) {
        // 创建新远程服务
        GostDto createResult = GostUtil.UpdateRemoteService(outNode.getId(), serviceName, forward.getOutPort(), forward.getRemoteAddr(), protocol, strategy, interfaceName);
        if (createResult.getMsg().contains(GOST_NOT_FOUND_MSG)) {
            createResult = GostUtil.AddRemoteService(outNode.getId(), serviceName, forward.getOutPort(), forward.getRemoteAddr(), protocol, strategy, interfaceName);
        }
        return isGostOperationSuccess(createResult) ? R.ok() : R.err(createResult.getMsg());
    }

    /**
     * 更新主服务
     */
    private R updateMainService(Node inNode, String serviceName, Forward forward, Integer limiter, Integer tunnelType, Tunnel tunnel, String strategy, String interfaceName) {
        GostDto result = GostUtil.UpdateService(inNode.getId(), serviceName, forward.getInPort(), limiter, forward.getRemoteAddr(), tunnelType, tunnel, strategy, interfaceName);

        if (result.getMsg().contains(GOST_NOT_FOUND_MSG)) {
            result = GostUtil.AddService(inNode.getId(), serviceName, forward.getInPort(), limiter, forward.getRemoteAddr(), tunnelType, tunnel, strategy, interfaceName);
        }

        return isGostOperationSuccess(result) ? R.ok() : R.err(result.getMsg());
    }

    /**
     * 更新转发状态为错误
     */
    private void updateForwardStatusToError(Forward forward) {
        forward.setStatus(FORWARD_STATUS_ERROR);
        this.updateById(forward);
    }

    /**
     * 获取用户隧道关系
     */
    private UserTunnel getUserTunnel(Integer userId, Integer tunnelId) {
        return userTunnelService.getOne(new QueryWrapper<UserTunnel>()
                .eq("user_id", userId)
                .eq("tunnel_id", tunnelId));
    }

    /**
     * 检查隧道是否发生变化
     */
    private boolean isTunnelChanged(Forward existForward, ForwardUpdateDto updateDto) {
        return !existForward.getTunnelId().equals(updateDto.getTunnelId());
    }

    /**
     * 检查Gost操作是否成功
     */
    private boolean isGostOperationSuccess(GostDto gostResult) {
        return Objects.equals(gostResult.getMsg(), GOST_SUCCESS_MSG);
    }

    private boolean isGostNotFound(GostDto gostResult) {
        return gostResult.getMsg() != null && gostResult.getMsg().contains(GOST_NOT_FOUND_MSG);
    }

    private String tunnelStrategy(Tunnel tunnel) {
        String strategy = tunnel == null ? null : tunnel.getStrategy();
        return strategy == null || strategy.trim().isEmpty() ? DEFAULT_STRATEGY : strategy.trim().toLowerCase(Locale.ROOT);
    }

    private String buildNodeRemoteAddr(List<Node> nodes, Integer port) {
        return nodes.stream()
                .map(node -> formatHostPort(firstNodeAddress(node), port))
                .collect(Collectors.joining(","));
    }

    private String firstNodeAddress(Node node) {
        if (node.getServerIp() != null && !node.getServerIp().trim().isEmpty()) {
            return node.getServerIp().trim();
        }
        return node.getIp() == null ? "" : node.getIp().trim();
    }

    private String formatHostPort(String host, Integer port) {
        String value = host == null ? "" : host.trim();
        if (value.contains(":") && !value.startsWith("[")) {
            value = "[" + value + "]";
        }
        return value + ":" + port;
    }


    /**
     * 检查指定的入口端口是否可用（可排除指定的转发ID）
     */
    private boolean isInPortAvailable(Tunnel tunnel, Integer port, Long excludeForwardId) {
        return isPortAvailableOnNodes(resolveTunnelNodes(tunnel.getInGroupId(), tunnel.getInNodeId()), port, excludeForwardId);
    }

    /**
     * 为隧道分配一个可用的入口端口（可排除指定的转发ID）
     */
    private Integer allocateInPort(Tunnel tunnel, Long excludeForwardId) {
        return allocatePortForNodes(resolveTunnelNodes(tunnel.getInGroupId(), tunnel.getInNodeId()), excludeForwardId);
    }

    /**
     * 为隧道分配一个可用的出口端口（可排除指定的转发ID）
     */
    private Integer allocateOutPort(Tunnel tunnel, Long excludeForwardId) {
        return allocatePortForNodes(resolveTunnelNodes(tunnel.getOutGroupId(), tunnel.getOutNodeId()), excludeForwardId);
    }

    private boolean isPortAvailableOnNodes(List<Node> nodes, Integer port, Long excludeForwardId) {
        if (nodes.isEmpty()) {
            return false;
        }
        for (Node node : nodes) {
            if (port < node.getPortSta() || port > node.getPortEnd()) {
                return false;
            }
            if (getAllUsedPortsOnNode(node.getId(), excludeForwardId).contains(port)) {
                return false;
            }
        }
        return true;
    }

    private Integer allocatePortForNodes(List<Node> nodes, Long excludeForwardId) {
        if (nodes.isEmpty()) {
            return null;
        }
        int start = nodes.stream().map(Node::getPortSta).max(Integer::compareTo).orElse(0);
        int end = nodes.stream().map(Node::getPortEnd).min(Integer::compareTo).orElse(0);
        if (start <= 0 || end <= 0 || start > end) {
            return null;
        }
        Map<Long, Set<Integer>> usedPortsByNode = new HashMap<>();
        for (Node node : nodes) {
            usedPortsByNode.put(node.getId(), getAllUsedPortsOnNode(node.getId(), excludeForwardId));
        }
        for (int port = start; port <= end; port++) {
            boolean used = false;
            for (Node node : nodes) {
                if (usedPortsByNode.get(node.getId()).contains(port)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return port;
            }
        }
        return null;
    }

    /**
     * 获取指定节点上所有已被占用的端口（包括入口和出口端口）
     *
     * @param nodeId           节点ID
     * @param excludeForwardId 要排除的转发ID
     * @return 已占用的端口集合
     */
    private Set<Integer> getAllUsedPortsOnNode(Long nodeId, Long excludeForwardId) {
        Set<Integer> usedPorts = new HashSet<>();
        Set<Long> inTunnelIds = new HashSet<>();
        Set<Long> outTunnelIds = new HashSet<>();

        for (Tunnel tunnel : tunnelService.list()) {
            if (tunnelContainsNode(tunnel.getInGroupId(), tunnel.getInNodeId(), nodeId)) {
                inTunnelIds.add(tunnel.getId());
            }
            if (tunnel.getType() == TUNNEL_TYPE_TUNNEL_FORWARD && tunnelContainsNode(tunnel.getOutGroupId(), tunnel.getOutNodeId(), nodeId)) {
                outTunnelIds.add(tunnel.getId());
            }
        }
        collectForwardPorts(inTunnelIds, excludeForwardId, true, usedPorts);
        collectForwardPorts(outTunnelIds, excludeForwardId, false, usedPorts);
        collectAggregateForwardPortsOnNode(nodeId, usedPorts);
        return usedPorts;
    }

    private void collectForwardPorts(Set<Long> tunnelIds, Long excludeForwardId, boolean entry, Set<Integer> usedPorts) {
        if (tunnelIds.isEmpty()) {
            return;
        }
        QueryWrapper<Forward> wrapper = new QueryWrapper<Forward>().in("tunnel_id", tunnelIds);
        if (excludeForwardId != null) {
            wrapper.ne("id", excludeForwardId);
        }
        for (Forward forward : this.list(wrapper)) {
            Integer port = entry ? forward.getInPort() : forward.getOutPort();
            if (port != null) {
                usedPorts.add(port);
            }
        }
    }

    private boolean tunnelContainsNode(Long groupId, Long nodeId, Long targetNodeId) {
        if (groupId != null) {
            AggregateNodeGroup group = aggregateNodeGroupService.getById(groupId);
            return aggregateGroupContainsNode(group, targetNodeId);
        }
        return Objects.equals(nodeId, targetNodeId);
    }

    private void collectAggregateForwardPortsOnNode(Long nodeId, Set<Integer> usedPorts) {
        List<AggregateForward> aggregateForwards = aggregateForwardMapper.selectList(new QueryWrapper<>());
        for (AggregateForward aggregateForward : aggregateForwards) {
            AggregateNodeGroup group = aggregateNodeGroupService.getById(aggregateForward.getEntryGroupId());
            if (!aggregateGroupContainsNode(group, nodeId)) {
                continue;
            }
            for (int port = aggregateForward.getEntryPortStart(); port <= aggregateForward.getEntryPortEnd(); port++) {
                usedPorts.add(port);
            }
        }
    }

    private boolean aggregateGroupContainsNode(AggregateNodeGroup group, Long nodeId) {
        if (group == null) {
            return false;
        }
        return aggregateNodeGroupService.parseNodeIds(group).contains(nodeId);
    }

    /**
     * 构建服务名称，优化后减少重复查询
     */
    private String buildServiceName(Long forwardId, Integer userId, UserTunnel userTunnel) {
        int userTunnelId = (userTunnel != null) ? userTunnel.getId() : 0;
        return forwardId + "_" + userId + "_" + userTunnelId;
    }


    public void updateForwardA(Forward forward) {
        Tunnel tunnel = validateTunnel(forward.getTunnelId());
        if (tunnel == null) {
            return;
        }
        UserTunnel userTunnel = getUserTunnel(forward.getUserId(), tunnel.getId().intValue());
        NodeInfo nodeInfo = getRequiredNodes(tunnel);
        if (nodeInfo.isHasError()) {
            return;
        }
        Integer limiter;
        if (userTunnel == null) {
            limiter = null;
        } else {
            limiter = userTunnel.getSpeedId();
        }
        R result = updateGostServices(forward, tunnel, limiter, nodeInfo, userTunnel);
        if (result.getCode() != 0) {
            log.warn("重建转发 {} 的Gost服务失败: {}", forward.getId(), result.getMsg());
        }
    }


    // ========== 内部数据类 ==========

    /**
     * 用户信息封装类
     */
    @Data
    private static class UserInfo {
        private final Integer userId;
        private final Integer roleId;
        private final String userName;
    }

    /**
     * 用户权限检查结果
     */
    @Data
    private static class UserPermissionResult {
        private final boolean hasError;
        private final String errorMessage;
        private final Integer limiter;
        private final UserTunnel userTunnel;

        private UserPermissionResult(boolean hasError, String errorMessage, Integer limiter, UserTunnel userTunnel) {
            this.hasError = hasError;
            this.errorMessage = errorMessage;
            this.limiter = limiter;
            this.userTunnel = userTunnel;
        }

        public static UserPermissionResult success(Integer limiter, UserTunnel userTunnel) {
            return new UserPermissionResult(false, null, limiter, userTunnel);
        }

        public static UserPermissionResult error(String errorMessage) {
            return new UserPermissionResult(true, errorMessage, null, null);
        }
    }

    /**
     * 端口分配结果
     */
    @Data
    private static class PortAllocation {
        private final boolean hasError;
        private final String errorMessage;
        private final Integer inPort;
        private final Integer outPort;

        private PortAllocation(boolean hasError, String errorMessage, Integer inPort, Integer outPort) {
            this.hasError = hasError;
            this.errorMessage = errorMessage;
            this.inPort = inPort;
            this.outPort = outPort;
        }

        public static PortAllocation success(Integer inPort, Integer outPort) {
            return new PortAllocation(false, null, inPort, outPort);
        }

        public static PortAllocation error(String errorMessage) {
            return new PortAllocation(true, errorMessage, null, null);
        }
    }

    /**
     * 节点信息封装类
     */
    @Data
    private static class NodeInfo {
        private final boolean hasError;
        private final String errorMessage;
        private final Node inNode;
        private final Node outNode;
        private final List<Node> inNodes;
        private final List<Node> outNodes;

        private NodeInfo(boolean hasError, String errorMessage, List<Node> inNodes, List<Node> outNodes) {
            this.hasError = hasError;
            this.errorMessage = errorMessage;
            this.inNodes = inNodes == null ? Collections.emptyList() : inNodes;
            this.outNodes = outNodes == null ? Collections.emptyList() : outNodes;
            this.inNode = this.inNodes.isEmpty() ? null : this.inNodes.get(0);
            this.outNode = this.outNodes.isEmpty() ? null : this.outNodes.get(0);
        }

        public static NodeInfo success(List<Node> inNodes, List<Node> outNodes) {
            return new NodeInfo(false, null, inNodes, outNodes);
        }

        public static NodeInfo error(String errorMessage) {
            return new NodeInfo(true, errorMessage, Collections.emptyList(), Collections.emptyList());
        }
    }

    /**
     * 诊断结果数据类
     */
    @Data
    public static class DiagnosisResult {
        private Long nodeId;
        private String nodeName;
        private String targetIp;
        private Integer targetPort;
        private String description;
        private boolean success;
        private String message;
        private double averageTime;
        private double packetLoss;
        private long timestamp;
    }
}
