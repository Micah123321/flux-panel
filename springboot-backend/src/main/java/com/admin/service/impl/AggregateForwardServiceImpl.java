package com.admin.service.impl;

import com.admin.common.dto.AggregateForwardDto;
import com.admin.common.dto.GostDto;
import com.admin.common.lang.R;
import com.admin.common.utils.GostUtil;
import com.admin.entity.AggregateForward;
import com.admin.entity.AggregateNodeGroup;
import com.admin.entity.Forward;
import com.admin.entity.Node;
import com.admin.entity.Tunnel;
import com.admin.mapper.AggregateForwardMapper;
import com.admin.service.AggregateForwardService;
import com.admin.service.AggregateNodeGroupService;
import com.admin.service.ForwardService;
import com.admin.service.NodeService;
import com.admin.service.TunnelService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AggregateForwardServiceImpl extends ServiceImpl<AggregateForwardMapper, AggregateForward> implements AggregateForwardService {

    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_PAUSED = 0;
    private static final String MODE_LOAD_BALANCE = "load_balance";
    private static final String MODE_FAILOVER = "failover";
    private static final String GOST_SUCCESS_MSG = "OK";
    // ha-min: 默认节点范围 50000-60000 是 10001 个端口；超过该上限应改为后台批处理与进度回滚。
    private static final int MAX_PORT_SPAN = 10001;

    @Resource
    private AggregateNodeGroupService aggregateNodeGroupService;

    @Resource
    private NodeService nodeService;

    @Resource
    private TunnelService tunnelService;

    @Resource
    private ForwardService forwardService;

    @Override
    public R createForward(AggregateForwardDto dto) {
        ValidationContext context = validate(dto);
        if (context.isHasError()) {
            return R.err(context.getErrorMessage());
        }

        AggregateForward forward = buildForward(dto, new AggregateForward());
        long now = System.currentTimeMillis();
        forward.setCreatedTime(now);
        forward.setUpdatedTime(now);
        forward.setStatus(STATUS_ACTIVE);
        forward.setInFlow(0L);
        forward.setOutFlow(0L);
        if (!save(forward)) {
            return R.err("聚合转发创建失败");
        }

        R deployResult = deployForward(forward, context);
        if (deployResult.getCode() != 0) {
            R cleanupResult = deleteForwardServices(forward, context);
            forward.setStatus(STATUS_PAUSED);
            updateById(forward);
            if (cleanupResult.getCode() != 0) {
                return R.err(deployResult.getMsg() + "；清理失败，已保留暂停记录: " + cleanupResult.getMsg());
            }
            if (!removeById(forward.getId())) {
                return R.err(deployResult.getMsg() + "；服务已清理，但临时记录删除失败，已置为暂停");
            }
            return deployResult;
        }
        return R.ok(enrichForward(forward));
    }

    @Override
    public R updateForward(AggregateForwardDto dto) {
        if (dto.getId() == null) {
            return R.err("聚合转发ID不能为空");
        }
        AggregateForward existing = getById(dto.getId());
        if (existing == null) {
            return R.err("聚合转发不存在");
        }
        ValidationContext context = validate(dto);
        if (context.isHasError()) {
            return R.err(context.getErrorMessage());
        }

        AggregateForward oldState = copyForward(existing);
        ValidationContext oldContext = resolveServiceContext(oldState);
        if (oldState.getStatus() == STATUS_ACTIVE && oldContext.isHasError()) {
            return R.err("无法清理原聚合转发服务: " + oldContext.getErrorMessage());
        }
        R deleteOldResult = oldState.getStatus() == STATUS_ACTIVE ? deleteForwardServices(oldState, oldContext) : R.ok();
        if (deleteOldResult.getCode() != 0) {
            return deleteOldResult;
        }

        AggregateForward updated = buildForward(dto, existing);
        updated.setUpdatedTime(System.currentTimeMillis());
        if (!updateById(updated)) {
            R restoreResult = restoreOldServices(oldState, oldContext);
            if (restoreResult.getCode() != 0) {
                return R.err("聚合转发更新失败，旧服务恢复失败: " + restoreResult.getMsg());
            }
            return R.err("聚合转发更新失败");
        }

        R deployResult = updated.getStatus() == STATUS_ACTIVE ? deployForward(updated, context) : R.ok();
        if (deployResult.getCode() != 0) {
            R cleanupResult = deleteForwardServices(updated, context);
            if (cleanupResult.getCode() != 0) {
                return R.err(deployResult.getMsg() + "；新服务清理失败: " + cleanupResult.getMsg());
            }
            if (!updateById(oldState)) {
                return R.err(deployResult.getMsg() + "；数据库回滚失败，旧服务未恢复");
            }
            R restoreResult = restoreOldServices(oldState, oldContext);
            if (restoreResult.getCode() != 0) {
                return R.err(deployResult.getMsg() + "；旧服务恢复失败: " + restoreResult.getMsg());
            }
            return deployResult;
        }
        return R.ok(enrichForward(updated));
    }

    @Override
    public R deleteForward(Long id) {
        AggregateForward forward = getById(id);
        if (forward == null) {
            return R.err("聚合转发不存在");
        }
        ValidationContext context = resolveServiceContext(forward);
        boolean deletedServices = false;
        if (forward.getStatus() == STATUS_ACTIVE) {
            if (context.isHasError()) {
                return R.err("无法清理聚合转发服务: " + context.getErrorMessage());
            }
            R deleteResult = deleteForwardServices(forward, context);
            if (deleteResult.getCode() != 0) {
                return deleteResult;
            }
            deletedServices = true;
        }
        if (!removeById(id)) {
            if (deletedServices) {
                R restoreResult = restoreOldServices(forward, context);
                if (restoreResult.getCode() != 0) {
                    return R.err("聚合转发删除失败，服务恢复失败: " + restoreResult.getMsg());
                }
            }
            return R.err("聚合转发删除失败");
        }
        return R.ok("删除成功");
    }

    @Override
    public R listForwards() {
        List<AggregateForward> forwards = list(new QueryWrapper<AggregateForward>().orderByDesc("created_time"));
        List<Map<String, Object>> data = forwards.stream().map(this::enrichForward).collect(Collectors.toList());
        return R.ok(data);
    }

    @Override
    public R pauseForward(Long id) {
        AggregateForward forward = getById(id);
        if (forward == null) {
            return R.err("聚合转发不存在");
        }
        if (forward.getStatus() == STATUS_PAUSED) {
            return R.ok("已暂停");
        }
        ValidationContext context = resolveServiceContext(forward);
        if (context.isHasError()) {
            return R.err("无法清理聚合转发服务: " + context.getErrorMessage());
        }
        R deleteResult = deleteForwardServices(forward, context);
        if (deleteResult.getCode() != 0) {
            return deleteResult;
        }
        forward.setStatus(STATUS_PAUSED);
        forward.setUpdatedTime(System.currentTimeMillis());
        if (!updateById(forward)) {
            R restoreResult = restoreOldServices(forward, context);
            if (restoreResult.getCode() != 0) {
                return R.err("暂停失败，服务恢复失败: " + restoreResult.getMsg());
            }
            return R.err("暂停失败");
        }
        return R.ok("已暂停");
    }

    @Override
    public R resumeForward(Long id) {
        AggregateForward forward = getById(id);
        if (forward == null) {
            return R.err("聚合转发不存在");
        }
        if (forward.getStatus() == STATUS_ACTIVE) {
            return R.ok("已恢复");
        }
        ValidationContext context = validateEntity(forward);
        if (context.isHasError()) {
            return R.err(context.getErrorMessage());
        }
        R deployResult = deployForward(forward, context);
        if (deployResult.getCode() != 0) {
            deleteForwardServices(forward, context);
            return deployResult;
        }
        forward.setStatus(STATUS_ACTIVE);
        forward.setUpdatedTime(System.currentTimeMillis());
        if (!updateById(forward)) {
            R cleanupResult = deleteForwardServices(forward, context);
            if (cleanupResult.getCode() != 0) {
                return R.err("恢复失败，已创建服务清理失败: " + cleanupResult.getMsg());
            }
            return R.err("恢复失败");
        }
        return R.ok("已恢复");
    }

    private AggregateForward buildForward(AggregateForwardDto dto, AggregateForward forward) {
        forward.setName(dto.getName().trim());
        forward.setEntryGroupId(dto.getEntryGroupId());
        forward.setExitGroupId(dto.getExitGroupId());
        forward.setEntryAddresses(normalizeAddresses(dto.getEntryAddresses()));
        forward.setEntryPortStart(dto.getEntryPortStart());
        forward.setEntryPortEnd(dto.getEntryPortEnd());
        forward.setTargetPortStart(dto.getTargetPortStart());
        forward.setTargetPortEnd(dto.getTargetPortEnd());
        forward.setMode(dto.getMode());
        forward.setTrafficRatio(dto.getTrafficRatio() == null ? BigDecimal.ONE : dto.getTrafficRatio());
        forward.setInterfaceName(trimToNull(dto.getInterfaceName()));
        forward.setRemark(trimToNull(dto.getRemark()));
        return forward;
    }

    private ValidationContext validate(AggregateForwardDto dto) {
        if (dto.getEntryPortEnd() < dto.getEntryPortStart()) {
            return ValidationContext.error("入口端口范围不正确");
        }
        if (dto.getTargetPortEnd() < dto.getTargetPortStart()) {
            return ValidationContext.error("出口端口范围不正确");
        }
        int entrySpan = dto.getEntryPortEnd() - dto.getEntryPortStart() + 1;
        int targetSpan = dto.getTargetPortEnd() - dto.getTargetPortStart() + 1;
        if (entrySpan != targetSpan) {
            return ValidationContext.error("入口端口范围和出口端口范围数量必须一致");
        }
        if (entrySpan > MAX_PORT_SPAN) {
            return ValidationContext.error("一次聚合转发最多支持 " + MAX_PORT_SPAN + " 个端口");
        }
        if (!MODE_LOAD_BALANCE.equals(dto.getMode()) && !MODE_FAILOVER.equals(dto.getMode())) {
            return ValidationContext.error("转发模式不支持");
        }
        if (splitAddresses(dto.getEntryAddresses()).isEmpty()) {
            return ValidationContext.error("请填写入口IP或域名");
        }
        ValidationContext context = validateGroups(dto.getEntryGroupId(), dto.getExitGroupId());
        if (context.isHasError()) {
            return context;
        }
        R portValidation = validatePortAvailability(dto, context);
        if (portValidation.getCode() != 0) {
            return ValidationContext.error(portValidation.getMsg());
        }
        return context;
    }

    private ValidationContext validateEntity(AggregateForward forward) {
        AggregateForwardDto dto = new AggregateForwardDto();
        BeanUtils.copyProperties(forward, dto);
        return validate(dto);
    }

    private ValidationContext validateGroups(Long entryGroupId, Long exitGroupId) {
        AggregateNodeGroup entryGroup = aggregateNodeGroupService.getById(entryGroupId);
        if (entryGroup == null) {
            return ValidationContext.error("入口节点组不存在");
        }
        AggregateNodeGroup exitGroup = aggregateNodeGroupService.getById(exitGroupId);
        if (exitGroup == null) {
            return ValidationContext.error("出口节点组不存在");
        }
        List<Node> entryNodes = resolveNodes(entryGroup);
        if (entryNodes.isEmpty()) {
            return ValidationContext.error("入口节点组没有可用节点");
        }
        for (Node node : entryNodes) {
            if (!Objects.equals(node.getStatus(), STATUS_ACTIVE)) {
                return ValidationContext.error("入口节点未在线: " + node.getName());
            }
        }
        List<Node> exitNodes = resolveNodes(exitGroup);
        if (exitNodes.isEmpty()) {
            return ValidationContext.error("出口节点组没有可用节点");
        }
        for (Node node : exitNodes) {
            if (firstNonBlank(node.getServerIp(), node.getIp()).isEmpty()) {
                return ValidationContext.error("出口节点缺少可访问地址: " + node.getName());
            }
        }
        return ValidationContext.success(entryGroup, exitGroup, entryNodes, exitNodes);
    }

    private ValidationContext resolveServiceContext(AggregateForward forward) {
        AggregateNodeGroup entryGroup = aggregateNodeGroupService.getById(forward.getEntryGroupId());
        if (entryGroup == null) {
            return ValidationContext.error("入口节点组不存在");
        }
        AggregateNodeGroup exitGroup = aggregateNodeGroupService.getById(forward.getExitGroupId());
        if (exitGroup == null) {
            return ValidationContext.error("出口节点组不存在");
        }
        List<Node> entryNodes = resolveNodes(entryGroup);
        if (entryNodes.isEmpty()) {
            return ValidationContext.error("入口节点组没有可用节点");
        }
        List<Node> exitNodes = resolveNodes(exitGroup);
        if (exitNodes.isEmpty()) {
            return ValidationContext.error("出口节点组没有可用节点");
        }
        for (Node node : exitNodes) {
            if (firstNonBlank(node.getServerIp(), node.getIp()).isEmpty()) {
                return ValidationContext.error("出口节点缺少可访问地址: " + node.getName());
            }
        }
        return ValidationContext.success(entryGroup, exitGroup, entryNodes, exitNodes);
    }

    private List<Node> resolveNodes(AggregateNodeGroup group) {
        List<Node> nodes = new ArrayList<>();
        for (Long nodeId : aggregateNodeGroupService.parseNodeIds(group)) {
            Node node = nodeService.getById(nodeId);
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    private R validatePortAvailability(AggregateForwardDto dto, ValidationContext context) {
        for (Node entryNode : context.getEntryNodes()) {
            if (dto.getEntryPortStart() < entryNode.getPortSta() || dto.getEntryPortEnd() > entryNode.getPortEnd()) {
                return R.err("入口节点 " + entryNode.getName() + " 允许端口范围为 " + entryNode.getPortSta() + "-" + entryNode.getPortEnd());
            }
            Set<Integer> usedPorts = getUsedPortsOnNode(entryNode.getId(), dto.getId());
            for (int port = dto.getEntryPortStart(); port <= dto.getEntryPortEnd(); port++) {
                if (usedPorts.contains(port)) {
                    return R.err("入口节点 " + entryNode.getName() + " 的端口 " + port + " 已被占用");
                }
            }
        }
        return R.ok();
    }

    private Set<Integer> getUsedPortsOnNode(Long nodeId, Long excludeAggregateForwardId) {
        Set<Integer> usedPorts = new HashSet<>();
        collectOrdinaryForwardPorts(nodeId, usedPorts);
        collectAggregateForwardPorts(nodeId, excludeAggregateForwardId, usedPorts);
        return usedPorts;
    }

    private void collectOrdinaryForwardPorts(Long nodeId, Set<Integer> usedPorts) {
        List<Tunnel> inTunnels = tunnelService.list(new QueryWrapper<Tunnel>().eq("in_node_id", nodeId));
        if (!inTunnels.isEmpty()) {
            Set<Long> tunnelIds = inTunnels.stream().map(Tunnel::getId).collect(Collectors.toSet());
            for (Forward forward : forwardService.list(new QueryWrapper<Forward>().in("tunnel_id", tunnelIds))) {
                if (forward.getInPort() != null) {
                    usedPorts.add(forward.getInPort());
                }
            }
        }

        List<Tunnel> outTunnels = tunnelService.list(new QueryWrapper<Tunnel>().eq("out_node_id", nodeId));
        if (!outTunnels.isEmpty()) {
            Set<Long> tunnelIds = outTunnels.stream().map(Tunnel::getId).collect(Collectors.toSet());
            for (Forward forward : forwardService.list(new QueryWrapper<Forward>().in("tunnel_id", tunnelIds))) {
                if (forward.getOutPort() != null) {
                    usedPorts.add(forward.getOutPort());
                }
            }
        }
    }

    private void collectAggregateForwardPorts(Long nodeId, Long excludeAggregateForwardId, Set<Integer> usedPorts) {
        for (AggregateForward forward : list()) {
            if (excludeAggregateForwardId != null && excludeAggregateForwardId.equals(forward.getId())) {
                continue;
            }
            AggregateNodeGroup group = aggregateNodeGroupService.getById(forward.getEntryGroupId());
            if (group == null || !aggregateNodeGroupService.parseNodeIds(group).contains(nodeId)) {
                continue;
            }
            for (int port = forward.getEntryPortStart(); port <= forward.getEntryPortEnd(); port++) {
                usedPorts.add(port);
            }
        }
    }

    private R deployForward(AggregateForward forward, ValidationContext context) {
        if (context.isHasError()) {
            return R.err(context.getErrorMessage());
        }
        List<ServiceRef> created = new ArrayList<>();
        Tunnel listen = new Tunnel();
        listen.setTcpListenAddr("[::]");
        listen.setUdpListenAddr("[::]");
        String strategy = MODE_LOAD_BALANCE.equals(forward.getMode()) ? "round" : "fifo";

        for (int entryPort = forward.getEntryPortStart(); entryPort <= forward.getEntryPortEnd(); entryPort++) {
            int targetPort = forward.getTargetPortStart() + (entryPort - forward.getEntryPortStart());
            String remoteAddr = buildRemoteAddr(context.getExitNodes(), targetPort);
            for (Node entryNode : context.getEntryNodes()) {
                String serviceName = buildServiceName(forward.getId(), entryPort);
                GostDto result = GostUtil.AddService(entryNode.getId(), serviceName, entryPort, null, remoteAddr, 1, listen, strategy, forward.getInterfaceName());
                if (!GOST_SUCCESS_MSG.equals(result.getMsg())) {
                    deleteCreatedServices(created);
                    return R.err("入口节点 " + entryNode.getName() + " 创建服务失败: " + result.getMsg());
                }
                created.add(new ServiceRef(entryNode.getId(), serviceName));
            }
        }
        return R.ok();
    }

    private R deleteForwardServices(AggregateForward forward, ValidationContext context) {
        if (context.isHasError()) {
            return R.err(context.getErrorMessage());
        }
        for (int entryPort = forward.getEntryPortStart(); entryPort <= forward.getEntryPortEnd(); entryPort++) {
            String serviceName = buildServiceName(forward.getId(), entryPort);
            for (Node entryNode : context.getEntryNodes()) {
                GostDto result = GostUtil.DeleteService(entryNode.getId(), serviceName);
                if (!GOST_SUCCESS_MSG.equals(result.getMsg()) && (result.getMsg() == null || !result.getMsg().contains("not found"))) {
                    return R.err("入口节点 " + entryNode.getName() + " 删除服务失败: " + result.getMsg());
                }
            }
        }
        return R.ok();
    }

    private void deleteCreatedServices(List<ServiceRef> created) {
        for (ServiceRef ref : created) {
            GostUtil.DeleteService(ref.getNodeId(), ref.getServiceName());
        }
    }

    private R restoreOldServices(AggregateForward oldState, ValidationContext oldContext) {
        if (oldState.getStatus() == STATUS_ACTIVE) {
            return deployForward(oldState, oldContext);
        }
        return R.ok();
    }

    private String buildRemoteAddr(List<Node> exitNodes, int targetPort) {
        return exitNodes.stream()
                .map(node -> formatHostPort(firstNonBlank(node.getServerIp(), node.getIp()), targetPort))
                .collect(Collectors.joining(","));
    }

    private String formatHostPort(String host, int port) {
        String cleanHost = host == null ? "" : host.trim();
        if (cleanHost.contains(":") && !cleanHost.startsWith("[")) {
            cleanHost = "[" + cleanHost + "]";
        }
        return cleanHost + ":" + port;
    }

    private String buildServiceName(Long forwardId, int entryPort) {
        return "agf_" + forwardId + "_" + entryPort;
    }

    private Map<String, Object> enrichForward(AggregateForward forward) {
        AggregateNodeGroup entryGroup = aggregateNodeGroupService.getById(forward.getEntryGroupId());
        AggregateNodeGroup exitGroup = aggregateNodeGroupService.getById(forward.getExitGroupId());
        List<Node> entryNodes = entryGroup == null ? new ArrayList<>() : resolveNodes(entryGroup);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", forward.getId());
        data.put("name", forward.getName());
        data.put("entryGroupId", forward.getEntryGroupId());
        data.put("exitGroupId", forward.getExitGroupId());
        data.put("entryGroupName", entryGroup == null ? null : entryGroup.getName());
        data.put("exitGroupName", exitGroup == null ? null : exitGroup.getName());
        data.put("entryAddresses", forward.getEntryAddresses());
        data.put("entryAddressList", splitAddresses(forward.getEntryAddresses()));
        data.put("entryPortStart", forward.getEntryPortStart());
        data.put("entryPortEnd", forward.getEntryPortEnd());
        data.put("targetPortStart", forward.getTargetPortStart());
        data.put("targetPortEnd", forward.getTargetPortEnd());
        data.put("mode", forward.getMode());
        data.put("trafficRatio", forward.getTrafficRatio());
        data.put("inFlow", forward.getInFlow());
        data.put("outFlow", forward.getOutFlow());
        data.put("interfaceName", forward.getInterfaceName());
        data.put("remark", forward.getRemark());
        data.put("status", forward.getStatus());
        data.put("serviceCount", serviceCount(forward, entryNodes));
        data.put("accessAddresses", buildAccessAddresses(forward));
        data.put("createdTime", forward.getCreatedTime());
        data.put("updatedTime", forward.getUpdatedTime());
        return data;
    }

    private int serviceCount(AggregateForward forward, List<Node> entryNodes) {
        int nodeCount = entryNodes == null ? 0 : entryNodes.size();
        int span = forward.getEntryPortEnd() - forward.getEntryPortStart() + 1;
        return nodeCount * span * 2;
    }

    private List<String> buildAccessAddresses(AggregateForward forward) {
        List<String> result = new ArrayList<>();
        for (String address : splitAddresses(forward.getEntryAddresses())) {
            for (int port = forward.getEntryPortStart(); port <= forward.getEntryPortEnd(); port++) {
                result.add(formatHostPort(address, port));
            }
        }
        return result;
    }

    private String normalizeAddresses(String addresses) {
        return String.join(",", splitAddresses(addresses));
    }

    private List<String> splitAddresses(String addresses) {
        List<String> result = new ArrayList<>();
        if (addresses == null) {
            return result;
        }
        for (String value : addresses.split("[\\s,，]+")) {
            String address = value.trim();
            if (!address.isEmpty()) {
                result.add(address);
            }
        }
        return result;
    }

    private AggregateForward copyForward(AggregateForward source) {
        AggregateForward target = new AggregateForward();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    @Data
    private static class ServiceRef {
        private final Long nodeId;
        private final String serviceName;
    }

    @Data
    private static class ValidationContext {
        private boolean hasError;
        private String errorMessage;
        private AggregateNodeGroup entryGroup;
        private AggregateNodeGroup exitGroup;
        private List<Node> entryNodes = new ArrayList<>();
        private List<Node> exitNodes = new ArrayList<>();

        static ValidationContext error(String message) {
            ValidationContext context = new ValidationContext();
            context.setHasError(true);
            context.setErrorMessage(message);
            return context;
        }

        static ValidationContext success(AggregateNodeGroup entryGroup, AggregateNodeGroup exitGroup, List<Node> entryNodes, List<Node> exitNodes) {
            ValidationContext context = new ValidationContext();
            context.setEntryGroup(entryGroup);
            context.setExitGroup(exitGroup);
            context.setEntryNodes(entryNodes);
            context.setExitNodes(exitNodes);
            return context;
        }
    }
}
