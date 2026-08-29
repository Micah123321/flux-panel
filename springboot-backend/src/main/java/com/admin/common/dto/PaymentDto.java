package com.admin.common.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.Map;

public class PaymentDto {
    @Data
    public static class PaymentConfigRequest {
        @NotBlank(message = "支付渠道不能为空")
        private String channel;
        private String displayName;
        private Boolean enabled;
        private String payType;
        private String gatewayUrl;
        private String appId;
        private String merchantId;
        private String secretKey;
        private String privateKey;
        private String publicKey;
        private String apiKey;
        private String endpointSecret;
        private String serialNo;
        private String notifyUrl;
        private String returnUrl;
        private String cancelUrl;
        private String currency;
        private Integer status;
    }

    @Data
    public static class PaymentCreateResult {
        private boolean success;
        private String message;
        private String channel;
        private String paymentUrl;
        private String providerTradeNo;
        private Map<String, Object> data;

        public static PaymentCreateResult fail(String message) {
            PaymentCreateResult result = new PaymentCreateResult();
            result.setSuccess(false);
            result.setMessage(message);
            return result;
        }

        public static PaymentCreateResult ok(String channel, String paymentUrl, String providerTradeNo, Map<String, Object> data) {
            PaymentCreateResult result = new PaymentCreateResult();
            result.setSuccess(true);
            result.setChannel(channel);
            result.setPaymentUrl(paymentUrl);
            result.setProviderTradeNo(providerTradeNo);
            result.setData(data);
            return result;
        }
    }

    @Data
    public static class PaymentNotifyResult {
        private boolean success;
        private String message;
        private String channel;
        private String orderNo;
        private String providerTradeNo;
        private BigDecimal paidAmount;
        private Map<String, Object> rawData;

        public static PaymentNotifyResult fail(String channel, String message) {
            PaymentNotifyResult result = new PaymentNotifyResult();
            result.setSuccess(false);
            result.setChannel(channel);
            result.setMessage(message);
            return result;
        }

        public static PaymentNotifyResult ok(String channel, String orderNo, String providerTradeNo, BigDecimal paidAmount, Map<String, Object> rawData) {
            PaymentNotifyResult result = new PaymentNotifyResult();
            result.setSuccess(true);
            result.setChannel(channel);
            result.setOrderNo(orderNo);
            result.setProviderTradeNo(providerTradeNo);
            result.setPaidAmount(paidAmount);
            result.setRawData(rawData);
            return result;
        }
    }
}
