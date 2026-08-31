package com.admin.service;

import com.admin.common.dto.AggregateForwardDto;
import com.admin.common.lang.R;
import com.admin.entity.AggregateForward;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;

public interface AggregateForwardService extends IService<AggregateForward> {

    R createForward(AggregateForwardDto dto);

    R updateForward(AggregateForwardDto dto);

    R deleteForward(Long id);

    R listForwards();

    R pauseForward(Long id);

    R resumeForward(Long id);

    R syncNodeGroupForwards(Long groupId, List<Long> oldNodeIds, List<Long> newNodeIds);
}
