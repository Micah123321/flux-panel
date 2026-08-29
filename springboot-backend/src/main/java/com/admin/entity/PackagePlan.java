package com.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class PackagePlan implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer hidden;
    private BigDecimal price;
    private Integer type;
    private Integer durationMultiplier;
    private Long userGroupId;
    private Long flow;
    private Integer maxRules;
    private Integer speedMbps;
    private Integer ipLimit;
    private Integer connectionLimit;
    private String description;
    private Long createdTime;
    private Long updatedTime;
    private Integer status;
}
