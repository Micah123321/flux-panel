package com.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;

@Data
public class PaymentConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
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
    private Long createdTime;
    private Long updatedTime;
    private Integer status;
}
