package com.admin.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

/**
 * <p>
 *
 * </p>
 *
 * @since 2025-06-03
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class UserTunnel implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private Integer tunnelId;

    private Long flow;

    private Long inFlow;

    private Long outFlow;

    /** 每日流量限制（GiB），0=不限制 */
    private Long dailyFlow;

    /** 今日已用入站流量（字节） */
    private Long dailyInFlow;

    /** 今日已用出站流量（字节） */
    private Long dailyOutFlow;

    private Long flowResetTime;

    private Long expTime;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer speedId;

    private Integer num;

    private Integer status;

}
