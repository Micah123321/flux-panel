package com.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class InviteRewardRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long inviterUserId;
    private Long inviteeUserId;
    private BigDecimal rewardAmount;
    private Integer ratio;
    private Integer type;
    private Long createdTime;
    private Long updatedTime;
    private Integer status;
}
