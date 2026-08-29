package com.admin.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * <p>
 *
 * </p>
 *
 * @since 2025-06-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 创建时间（时间戳）
     */
    private Long createdTime;

    /**
     * 更新时间（时间戳）
     */
    private Long updatedTime;

    /**
     * 状态（0：正常，1：删除）
     */
    private Integer status;

    private String user;

    private String pwd;

    private Integer roleId;

    private Long expTime;

    private Long flow;

    private Long inFlow;

    private Long outFlow;

    /** 每日流量限制（GiB），0=不限制 */
    private Long dailyFlow;

    /** 今日已用入站流量（字节） */
    private Long dailyInFlow;

    /** 今日已用出站流量（字节） */
    private Long dailyOutFlow;

    private Integer num;

    private Long flowResetTime;

    private Long packagePlanId;

    private Long userGroupId;

    private Integer speedMbps;

    private Integer ipLimit;

    private Integer connectionLimit;

    private String inviteCode;

    private Long inviterUserId;

    private BigDecimal inviteBalance;

}
