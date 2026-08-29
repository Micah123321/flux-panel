package com.admin.common.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class AggregateNodeGroupDto {

    private Long id;

    @NotBlank(message = "节点组名称不能为空")
    private String name;

    @NotEmpty(message = "请选择节点组成员")
    private List<Long> nodeIds;

    private String remark;
}
