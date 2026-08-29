package com.admin.controller;

import com.admin.common.aop.LogAnnotation;
import com.admin.common.dto.PaymentDto.PaymentNotifyResult;
import com.admin.common.lang.R;
import com.admin.service.CommerceService;
import com.admin.service.PaymentService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import com.admin.common.utils.PaymentHttpUtil;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/payment")
public class PaymentController extends BaseController {
    @Resource
    private PaymentService paymentService;
    @Resource
    private CommerceService commerceService;

    @LogAnnotation
    @PostMapping("/notify/{channel}")
    public String notify(@PathVariable String channel, @RequestParam Map<String, String> params, HttpServletRequest request) {
        String body = readBody(request);
        PaymentNotifyResult notifyResult = paymentService.handleNotify(channel, params, body, headers(request));
        if (!notifyResult.isSuccess()) return paymentService.failResponse(channel);
        R result = commerceService.completePaidOrder(notifyResult);
        return result.getCode() == 0 ? paymentService.successResponse(channel) : paymentService.failResponse(channel);
    }

    @LogAnnotation
    @GetMapping("/notify/{channel}")
    public String notifyGet(@PathVariable String channel, @RequestParam Map<String, String> params, HttpServletRequest request) {
        PaymentNotifyResult notifyResult = paymentService.handleNotify(channel, params, "", headers(request));
        if (!notifyResult.isSuccess()) return paymentService.failResponse(channel);
        R result = commerceService.completePaidOrder(notifyResult);
        return result.getCode() == 0 ? paymentService.successResponse(channel) : paymentService.failResponse(channel);
    }

    private String readBody(HttpServletRequest request) {
        try {
            return PaymentHttpUtil.readUtf8(request.getInputStream());
        } catch (Exception e) {
            return "";
        }
    }

    private Map<String, String> headers(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }
}
