package com.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aggregate_forward")
public class AggregateForward extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String name;

    private Long entryGroupId;

    private Long exitGroupId;

    private String entryAddresses;

    private Integer entryPortStart;

    private Integer entryPortEnd;

    private Integer targetPortStart;

    private Integer targetPortEnd;

    private String mode;

    private BigDecimal trafficRatio;

    private Long inFlow;

    private Long outFlow;

    private String interfaceName;

    private String remark;
}
