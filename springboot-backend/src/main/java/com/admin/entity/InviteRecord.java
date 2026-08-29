package com.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;

@Data
public class InviteRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long inviterUserId;
    private Long inviteeUserId;
    private String inviteCode;
    private Long createdTime;
    private Long updatedTime;
    private Integer status;
}
