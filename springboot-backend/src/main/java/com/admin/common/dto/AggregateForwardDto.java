package com.admin.common.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class AggregateForwardDto {

    private Long id;

    @NotBlank(message = "聚合转发名称不能为空")
    private String name;

    @NotNull(message = "请选择入口节点组")
    private Long entryGroupId;

    @NotNull(message = "请选择出口节点组")
    private Long exitGroupId;

    @NotBlank(message = "请填写入口IP或域名")
    private String entryAddresses;

    @NotNull(message = "入口起始端口不能为空")
    @Min(value = 1, message = "入口起始端口必须大于0")
    @Max(value = 65535, message = "入口起始端口不能超过65535")
    private Integer entryPortStart;

    @NotNull(message = "入口结束端口不能为空")
    @Min(value = 1, message = "入口结束端口必须大于0")
    @Max(value = 65535, message = "入口结束端口不能超过65535")
    private Integer entryPortEnd;

    @NotNull(message = "出口起始端口不能为空")
    @Min(value = 1, message = "出口起始端口必须大于0")
    @Max(value = 65535, message = "出口起始端口不能超过65535")
    private Integer targetPortStart;

    @NotNull(message = "出口结束端口不能为空")
    @Min(value = 1, message = "出口结束端口必须大于0")
    @Max(value = 65535, message = "出口结束端口不能超过65535")
    private Integer targetPortEnd;

    @NotBlank(message = "请选择转发模式")
    private String mode;

    @DecimalMin(value = "0.1", message = "倍率不能小于0.1")
    private BigDecimal trafficRatio;

    private String interfaceName;

    private String remark;
}
