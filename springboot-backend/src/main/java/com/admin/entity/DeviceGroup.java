package com.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DeviceGroup implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String name;
    private String tunnelIds;
    private String description;
    private Long createdTime;
    private Long updatedTime;
    private Integer status;

    @TableField(exist = false)
    private List<Long> tunnelIdList;

    @TableField(exist = false)
    private String tunnelNames;
}
