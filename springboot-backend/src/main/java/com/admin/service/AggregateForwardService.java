package com.admin.service;

import com.admin.common.dto.AggregateForwardDto;
import com.admin.common.lang.R;
import com.admin.entity.AggregateForward;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AggregateForwardService extends IService<AggregateForward> {

    R createForward(AggregateForwardDto dto);

    R updateForward(AggregateForwardDto dto);

    R deleteForward(Long id);

    R listForwards();

    R pauseForward(Long id);

    R resumeForward(Long id);
}
