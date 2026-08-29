package com.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long packagePlanId;
    private String packageName;
    private BigDecimal originalAmount;
    private Integer discountRatio;
    private BigDecimal payableAmount;
    private BigDecimal inviteDeduction;
    private Integer status;
    private String paymentChannel;
    private String providerTradeNo;
    private String paymentUrl;
    private BigDecimal paidAmount;
    private Long redeemCodeId;
    private Long inviterUserId;
    private Integer rewardRatio;
    private BigDecimal rewardAmount;
    private Long completedTime;
    private Long createdTime;
    private Long updatedTime;
}
