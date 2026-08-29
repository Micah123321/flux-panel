package com.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;

@Data
public class RedeemCode implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long packagePlanId;
    private String packageName;
    private Integer discountRatio;
    private Integer totalTimes;
    private Integer usedTimes;
    private String code;
    private Long createdTime;
    private Long updatedTime;
    private Integer status;
}
