package com.admin.service.impl;

import com.admin.common.dto.AggregateNodeGroupDto;
import com.admin.common.lang.R;
import com.admin.entity.AggregateForward;
import com.admin.entity.AggregateNodeGroup;
import com.admin.entity.Node;
import com.admin.mapper.AggregateForwardMapper;
import com.admin.mapper.AggregateNodeGroupMapper;
import com.admin.service.AggregateNodeGroupService;
import com.admin.service.NodeService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AggregateNodeGroupServiceImpl extends ServiceImpl<AggregateNodeGroupMapper, AggregateNodeGroup> implements AggregateNodeGroupService {

    private static final int STATUS_ACTIVE = 1;

    @Resource
    private NodeService nodeService;

    @Resource
    private AggregateForwardMapper aggregateForwardMapper;

    @Override
    public R createGroup(AggregateNodeGroupDto dto) {
        List<Long> nodeIds = normalizeNodeIds(dto.getNodeIds());
        R validation = validateNodes(nodeIds);
        if (validation.getCode() != 0) {
            return validation;
        }

        AggregateNodeGroup group = new AggregateNodeGroup();
        group.setName(dto.getName().trim());
        group.setNodeIds(joinNodeIds(nodeIds));
        group.setRemark(trimToNull(dto.getRemark()));
        group.setStatus(STATUS_ACTIVE);
        long now = System.currentTimeMillis();
        group.setCreatedTime(now);
        group.setUpdatedTime(now);

        return save(group) ? R.ok(enrichGroup(group)) : R.err("节点组创建失败");
    }

    @Override
    public R updateGroup(AggregateNodeGroupDto dto) {
        if (dto.getId() == null) {
            return R.err("节点组ID不能为空");
        }
        AggregateNodeGroup group = getById(dto.getId());
        if (group == null) {
            return R.err("节点组不存在");
        }

        List<Long> nodeIds = normalizeNodeIds(dto.getNodeIds());
        R validation = validateNodes(nodeIds);
        if (validation.getCode() != 0) {
            return validation;
        }

        String nextNodeIds = joinNodeIds(nodeIds);
        if (!nextNodeIds.equals(group.getNodeIds()) && activeForwardReferenceCount(group.getId()) > 0) {
            return R.err("节点组正在被运行中的聚合转发使用，请先暂停相关聚合转发");
        }

        group.setName(dto.getName().trim());
        group.setNodeIds(nextNodeIds);
        group.setRemark(trimToNull(dto.getRemark()));
        group.setUpdatedTime(System.currentTimeMillis());
        return updateById(group) ? R.ok(enrichGroup(group)) : R.err("节点组更新失败");
    }

    @Override
    public R deleteGroup(Long id) {
        AggregateNodeGroup group = getById(id);
        if (group == null) {
            return R.err("节点组不存在");
        }
        Integer count = aggregateForwardMapper.selectCount(new QueryWrapper<AggregateForward>()
                .eq("entry_group_id", id).or().eq("exit_group_id", id));
        if (count != null && count > 0) {
            return R.err("节点组正在被聚合转发使用，不能删除");
        }
        return removeById(id) ? R.ok("删除成功") : R.err("节点组删除失败");
    }

    @Override
    public R listGroups() {
        List<AggregateNodeGroup> groups = list(new QueryWrapper<AggregateNodeGroup>().orderByDesc("created_time"));
        List<Map<String, Object>> data = groups.stream().map(this::enrichGroup).collect(Collectors.toList());
        return R.ok(data);
    }

    @Override
    public List<Long> parseNodeIds(AggregateNodeGroup group) {
        List<Long> ids = new ArrayList<>();
        if (group == null || group.getNodeIds() == null || group.getNodeIds().trim().isEmpty()) {
            return ids;
        }
        for (String value : group.getNodeIds().split(",")) {
            if (!value.trim().isEmpty()) {
                ids.add(Long.valueOf(value.trim()));
            }
        }
        return ids;
    }

    private int activeForwardReferenceCount(Long groupId) {
        Integer count = aggregateForwardMapper.selectCount(new QueryWrapper<AggregateForward>()
                .eq("status", STATUS_ACTIVE)
                .and(wrapper -> wrapper.eq("entry_group_id", groupId).or().eq("exit_group_id", groupId)));
        return count == null ? 0 : count;
    }

    private R validateNodes(List<Long> nodeIds) {
        if (nodeIds.isEmpty()) {
            return R.err("请选择节点组成员");
        }
        List<Node> nodes = nodeService.listByIds(nodeIds);
        if (nodes.size() != nodeIds.size()) {
            return R.err("节点组包含不存在的节点");
        }
        return R.ok();
    }

    private List<Long> normalizeNodeIds(List<Long> nodeIds) {
        Set<Long> unique = new LinkedHashSet<>();
        if (nodeIds != null) {
            for (Long nodeId : nodeIds) {
                if (nodeId != null) {
                    unique.add(nodeId);
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private String joinNodeIds(List<Long> nodeIds) {
        return nodeIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private Map<String, Object> enrichGroup(AggregateNodeGroup group) {
        List<Long> nodeIds = parseNodeIds(group);
        Map<Long, Node> nodeMap = nodeService.listByIds(nodeIds).stream()
                .collect(Collectors.toMap(Node::getId, node -> node));
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Long nodeId : nodeIds) {
            Node node = nodeMap.get(nodeId);
            if (node == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", node.getId());
            item.put("name", node.getName());
            item.put("ip", node.getIp());
            item.put("serverIp", node.getServerIp());
            item.put("portSta", node.getPortSta());
            item.put("portEnd", node.getPortEnd());
            item.put("status", node.getStatus());
            nodes.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", group.getId());
        data.put("name", group.getName());
        data.put("nodeIds", nodeIds);
        data.put("nodes", nodes);
        data.put("remark", group.getRemark());
        data.put("status", group.getStatus());
        data.put("createdTime", group.getCreatedTime());
        data.put("updatedTime", group.getUpdatedTime());
        return data;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
