package com.admin.service;

import com.admin.common.dto.PaymentDto.PaymentConfigRequest;
import com.admin.common.dto.PaymentDto.PaymentCreateResult;
import com.admin.common.dto.PaymentDto.PaymentNotifyResult;
import com.admin.common.lang.R;
import com.admin.entity.OrderRecord;

import java.util.Map;

public interface PaymentService {
    R listPaymentConfigs(boolean admin);
    R updatePaymentConfig(PaymentConfigRequest request);
    PaymentCreateResult createPayment(OrderRecord order);
    PaymentNotifyResult handleNotify(String channel, Map<String, String> params, String body, Map<String, String> headers);
    String successResponse(String channel);
    String failResponse(String channel);
}
