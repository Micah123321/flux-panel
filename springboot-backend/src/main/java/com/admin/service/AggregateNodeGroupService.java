package com.admin.service;

import com.admin.common.dto.AggregateNodeGroupDto;
import com.admin.common.lang.R;
import com.admin.entity.AggregateNodeGroup;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AggregateNodeGroupService extends IService<AggregateNodeGroup> {

    R createGroup(AggregateNodeGroupDto dto);

    R updateGroup(AggregateNodeGroupDto dto);

    R deleteGroup(Long id);

    R listGroups();

    R pruneOfflineNodes(Long groupId);

    R pruneOfflineNode(Long nodeId);

    List<Long> parseNodeIds(AggregateNodeGroup group);
}
