package com.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aggregate_node_group")
public class AggregateNodeGroup extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String name;

    private String nodeIds;

    private String remark;
}
