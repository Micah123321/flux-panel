package com.admin.common.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CommerceDto {

    @Data
    public static class IdRequest {
        @NotNull(message = "ID不能为空")
        private Long id;
    }

    @Data
    public static class PackagePlanRequest {
        private Long id;

        @NotBlank(message = "套餐名称不能为空")
        private String name;

        private Integer hidden;

        @NotNull(message = "价格不能为空")
        @DecimalMin(value = "0", message = "价格不能小于0")
        private BigDecimal price;

        private Integer type;

        @NotNull(message = "时长倍数不能为空")
        @Min(value = 1, message = "时长倍数必须大于0")
        private Integer durationMultiplier;

        private Long userGroupId;

        @NotNull(message = "流量不能为空")
        @Min(value = 0, message = "流量不能小于0")
        private Long flow;

        @Min(value = 0, message = "每日流量限制不能小于0")
        private Long dailyFlow;

        @NotNull(message = "最大规则数不能为空")
        @Min(value = 0, message = "最大规则数不能小于0")
        private Integer maxRules;

        @Min(value = 0, message = "用户限速不能小于0")
        private Integer speedMbps;

        @Min(value = 0, message = "IP限制不能小于0")
        private Integer ipLimit;

        @Min(value = 0, message = "连接数限制不能小于0")
        private Integer connectionLimit;

        private String description;
        private Integer status;
    }

    @Data
    public static class DeviceGroupRequest {
        private Long id;

        @NotBlank(message = "设备组名称不能为空")
        private String name;

        private List<Long> tunnelIds;
        private String description;
        private Integer status;
    }

    @Data
    public static class UserGroupRequest {
        private Long id;

        @NotBlank(message = "用户组名称不能为空")
        private String name;

        private String description;
        private Integer status;
    }

    @Data
    public static class BindTunnelsRequest {
        @NotNull(message = "设备组ID不能为空")
        private Long id;

        private List<Long> tunnelIds;
    }

    @Data
    public static class BindDeviceGroupsRequest {
        @NotNull(message = "用户组ID不能为空")
        private Long id;

        private List<Long> deviceGroupIds;
    }

    @Data
    public static class BatchRedeemCodeRequest {
        @NotNull(message = "套餐ID不能为空")
        private Long packagePlanId;

        @Min(value = 0, message = "折扣比例不能小于0")
        private Integer discountRatio;

        @Min(value = 1, message = "可用次数必须大于0")
        private Integer totalTimes;

        @Min(value = 1, message = "生成数量必须大于0")
        private Integer count;

        private List<String> codes;
    }

    @Data
    public static class CreateOrderRequest {
        @NotNull(message = "套餐ID不能为空")
        private Long packagePlanId;

        private String redeemCode;

        @NotBlank(message = "支付方式不能为空")
        private String paymentChannel;

        private Boolean useInviteBalance;
    }

    @Data
    public static class CompleteOrderRequest {
        @NotNull(message = "订单ID不能为空")
        private Long id;
    }

    @Data
    public static class RedeemRequest {
        @NotBlank(message = "兑换码不能为空")
        private String code;
    }

    @Data
    public static class InviteConfigRequest {
        @NotNull(message = "邀请比例不能为空")
        @Min(value = 0, message = "邀请比例不能小于0")
        private Integer inviteRatio;

        @NotNull(message = "续费邀请比例不能为空")
        @Min(value = 0, message = "续费邀请比例不能小于0")
        private Integer inviteRenewalRatio;
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        private String user;

        @NotBlank(message = "密码不能为空")
        private String pwd;

        private String inviteCode;
    }
}
