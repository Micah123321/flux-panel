package com.admin.service.impl;

import com.admin.common.dto.PaymentDto.PaymentConfigRequest;
import com.admin.common.dto.PaymentDto.PaymentCreateResult;
import com.admin.common.dto.PaymentDto.PaymentNotifyResult;
import com.admin.common.lang.R;
import com.admin.common.utils.PaymentHttpUtil;
import com.admin.common.utils.PaymentSignUtil;
import com.admin.entity.OrderRecord;
import com.admin.entity.PaymentConfig;
import com.admin.mapper.PaymentConfigMapper;
import com.admin.service.PaymentService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class PaymentServiceImpl implements PaymentService {
    public static final String CHANNEL_EASYPAY = "easypay";
    public static final String CHANNEL_ALIPAY = "alipay";
    public static final String CHANNEL_WECHAT = "wechat";
    public static final String CHANNEL_STRIPE = "stripe";

    private static final int ACTIVE = 1;
    private static final Set<String> CHANNELS = Set.of(CHANNEL_EASYPAY, CHANNEL_ALIPAY, CHANNEL_WECHAT, CHANNEL_STRIPE);

    @Resource
    private PaymentConfigMapper paymentConfigMapper;

    @Override
    public R listPaymentConfigs(boolean admin) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String channel : orderedChannels()) {
            PaymentConfig config = getOrDefaultConfig(channel);
            result.add(admin ? toAdminView(config) : toUserView(config));
        }
        if (!admin) {
            result.removeIf(item -> !Boolean.TRUE.equals(item.get("enabled")));
        }
        return R.ok(result);
    }

    @Override
    public R updatePaymentConfig(PaymentConfigRequest request) {
        String channel = normalizeChannel(request.getChannel());
        if (!CHANNELS.contains(channel)) return R.err("不支持的支付渠道");

        PaymentConfig old = findConfig(channel);
        PaymentConfig config = old == null ? new PaymentConfig() : old;
        long now = System.currentTimeMillis();
        if (config.getId() == null) {
            config.setChannel(channel);
            config.setCreatedTime(now);
        }
        config.setDisplayName(defaultText(request.getDisplayName(), defaultDisplayName(channel)));
        config.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        config.setPayType(defaultText(request.getPayType(), defaultPayType(channel)));
        config.setGatewayUrl(defaultText(request.getGatewayUrl(), defaultGateway(channel)));
        config.setAppId(clean(request.getAppId()));
        config.setMerchantId(clean(request.getMerchantId()));
        config.setSecretKey(mergeSecret(old == null ? null : old.getSecretKey(), request.getSecretKey()));
        config.setPrivateKey(mergeSecret(old == null ? null : old.getPrivateKey(), request.getPrivateKey()));
        config.setPublicKey(mergeSecret(old == null ? null : old.getPublicKey(), request.getPublicKey()));
        config.setApiKey(mergeSecret(old == null ? null : old.getApiKey(), request.getApiKey()));
        config.setEndpointSecret(mergeSecret(old == null ? null : old.getEndpointSecret(), request.getEndpointSecret()));
        config.setSerialNo(clean(request.getSerialNo()));
        config.setNotifyUrl(clean(request.getNotifyUrl()));
        config.setReturnUrl(clean(request.getReturnUrl()));
        config.setCancelUrl(clean(request.getCancelUrl()));
        config.setCurrency(defaultText(request.getCurrency(), defaultCurrency(channel)));
        config.setUpdatedTime(now);
        config.setStatus(request.getStatus() == null ? ACTIVE : request.getStatus());

        if (config.getId() == null) paymentConfigMapper.insert(config); else paymentConfigMapper.updateById(config);
        return R.ok(toAdminView(config));
    }

    @Override
    public PaymentCreateResult createPayment(OrderRecord order) {
        String channel = normalizeChannel(order.getPaymentChannel());
        if (!CHANNELS.contains(channel)) return PaymentCreateResult.fail("请选择有效支付方式");
        PaymentConfig config = findConfig(channel);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled()) || !Objects.equals(config.getStatus(), ACTIVE)) {
            return PaymentCreateResult.fail("支付方式未启用");
        }
        try {
            if (CHANNEL_EASYPAY.equals(channel)) return createEasyPay(order, config);
            if (CHANNEL_ALIPAY.equals(channel)) return createAlipay(order, config);
            if (CHANNEL_WECHAT.equals(channel)) return createWechat(order, config);
            if (CHANNEL_STRIPE.equals(channel)) return createStripe(order, config);
            return PaymentCreateResult.fail("不支持的支付渠道");
        } catch (Exception e) {
            return PaymentCreateResult.fail("创建支付失败: " + e.getMessage());
        }
    }

    @Override
    public PaymentNotifyResult handleNotify(String channel, Map<String, String> params, String body, Map<String, String> headers) {
        String normalizedChannel = normalizeChannel(channel);
        PaymentConfig config = findConfig(normalizedChannel);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) return PaymentNotifyResult.fail(normalizedChannel, "支付方式未启用");
        try {
            if (CHANNEL_EASYPAY.equals(normalizedChannel)) return verifyEasyPay(config, params);
            if (CHANNEL_ALIPAY.equals(normalizedChannel)) return verifyAlipay(config, params);
            if (CHANNEL_WECHAT.equals(normalizedChannel)) return verifyWechat(config, body, headers);
            if (CHANNEL_STRIPE.equals(normalizedChannel)) return verifyStripe(config, body, headers);
            return PaymentNotifyResult.fail(normalizedChannel, "不支持的支付渠道");
        } catch (Exception e) {
            return PaymentNotifyResult.fail(normalizedChannel, e.getMessage());
        }
    }

    @Override
    public String successResponse(String channel) {
        if (CHANNEL_WECHAT.equals(normalizeChannel(channel))) return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
        return "success";
    }

    @Override
    public String failResponse(String channel) {
        if (CHANNEL_WECHAT.equals(normalizeChannel(channel))) return "{\"code\":\"FAIL\",\"message\":\"失败\"}";
        return "fail";
    }

    private PaymentCreateResult createEasyPay(OrderRecord order, PaymentConfig config) {
        String gateway = require(config.getGatewayUrl(), "EasyPay网关");
        String submitUrl = gateway.endsWith(".php") ? gateway : trimSlash(gateway) + "/submit.php";
        Map<String, String> params = new HashMap<>();
        params.put("pid", require(config.getMerchantId(), "EasyPay商户ID"));
        params.put("type", defaultText(config.getPayType(), "alipay"));
        params.put("out_trade_no", order.getOrderNo());
        params.put("notify_url", require(config.getNotifyUrl(), "EasyPay异步通知地址"));
        params.put("return_url", require(config.getReturnUrl(), "EasyPay同步返回地址"));
        params.put("name", order.getPackageName());
        params.put("money", amountText(order.getPayableAmount()));
        String sign = PaymentSignUtil.md5Lower(PaymentSignUtil.canonicalQuery(params, Set.of(), false) + require(config.getSecretKey(), "EasyPay密钥"));
        params.put("sign", sign);
        params.put("sign_type", "MD5");
        String url = submitUrl + "?" + PaymentSignUtil.canonicalQuery(params, Set.of(), true);
        return PaymentCreateResult.ok(CHANNEL_EASYPAY, url, null, PaymentSignUtil.mapOf("mode", "redirect"));
    }

    private PaymentCreateResult createAlipay(OrderRecord order, PaymentConfig config) {
        String gateway = defaultText(config.getGatewayUrl(), defaultGateway(CHANNEL_ALIPAY));
        JSONObject biz = new JSONObject();
        biz.put("out_trade_no", order.getOrderNo());
        biz.put("total_amount", amountText(order.getPayableAmount()));
        biz.put("subject", order.getPackageName());
        biz.put("product_code", "FAST_INSTANT_TRADE_PAY");
        Map<String, String> params = new HashMap<>();
        params.put("app_id", require(config.getAppId(), "支付宝App ID"));
        params.put("method", "alipay.trade.page.pay");
        params.put("charset", "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        params.put("version", "1.0");
        params.put("notify_url", require(config.getNotifyUrl(), "支付宝异步通知地址"));
        params.put("return_url", require(config.getReturnUrl(), "支付宝同步返回地址"));
        params.put("biz_content", biz.toJSONString());
        String content = PaymentSignUtil.canonicalQuery(params, Set.of("sign"), false);
        params.put("sign", PaymentSignUtil.rsaSha256Sign(content, require(config.getPrivateKey(), "支付宝应用私钥")));
        String url = trimQuestion(gateway) + "?" + PaymentSignUtil.canonicalQuery(params, Set.of(), true);
        return PaymentCreateResult.ok(CHANNEL_ALIPAY, url, null, PaymentSignUtil.mapOf("mode", "redirect"));
    }

    private PaymentCreateResult createWechat(OrderRecord order, PaymentConfig config) throws Exception {
        String gateway = defaultText(config.getGatewayUrl(), defaultGateway(CHANNEL_WECHAT));
        JSONObject amount = new JSONObject();
        amount.put("total", amountToCents(order.getPayableAmount()));
        amount.put("currency", defaultText(config.getCurrency(), "CNY"));
        JSONObject body = new JSONObject();
        body.put("appid", require(config.getAppId(), "微信App ID"));
        body.put("mchid", require(config.getMerchantId(), "微信商户号"));
        body.put("description", order.getPackageName());
        body.put("out_trade_no", order.getOrderNo());
        body.put("notify_url", require(config.getNotifyUrl(), "微信支付通知地址"));
        body.put("amount", amount);
        String bodyText = body.toJSONString();
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Authorization", wechatAuthorization(config, gateway, bodyText));
        JSONObject response = JSON.parseObject(PaymentHttpUtil.postJson(gateway, bodyText, headers));
        String codeUrl = response.getString("code_url");
        if (!StringUtils.hasText(codeUrl)) throw new IllegalStateException(defaultText(response.getString("message"), "微信支付未返回二维码链接"));
        return PaymentCreateResult.ok(CHANNEL_WECHAT, codeUrl, null, PaymentSignUtil.mapOf("mode", "qrcode"));
    }

    private PaymentCreateResult createStripe(OrderRecord order, PaymentConfig config) throws Exception {
        String gateway = defaultText(config.getGatewayUrl(), defaultGateway(CHANNEL_STRIPE));
        Map<String, String> form = new LinkedHashMap<>();
        form.put("mode", "payment");
        form.put("client_reference_id", order.getOrderNo());
        form.put("success_url", require(config.getReturnUrl(), "Stripe成功返回地址"));
        form.put("cancel_url", require(config.getCancelUrl(), "Stripe取消返回地址"));
        form.put("line_items[0][quantity]", "1");
        form.put("line_items[0][price_data][currency]", defaultText(config.getCurrency(), "cny").toLowerCase());
        form.put("line_items[0][price_data][unit_amount]", String.valueOf(amountToCents(order.getPayableAmount())));
        form.put("line_items[0][price_data][product_data][name]", order.getPackageName());
        form.put("metadata[order_no]", order.getOrderNo());
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + require(config.getApiKey(), "Stripe Secret Key"));
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        JSONObject response = JSON.parseObject(PaymentHttpUtil.postForm(gateway, form, headers));
        String url = response.getString("url");
        String sessionId = response.getString("id");
        if (!StringUtils.hasText(url)) throw new IllegalStateException(defaultText(response.getString("error"), "Stripe未返回支付链接"));
        return PaymentCreateResult.ok(CHANNEL_STRIPE, url, sessionId, PaymentSignUtil.mapOf("mode", "redirect"));
    }

    private PaymentNotifyResult verifyEasyPay(PaymentConfig config, Map<String, String> params) {
        if (!"TRADE_SUCCESS".equals(params.get("trade_status"))) return PaymentNotifyResult.fail(CHANNEL_EASYPAY, "EasyPay交易未成功");
        if (!Objects.equals(params.get("pid"), require(config.getMerchantId(), "EasyPay商户ID"))) return PaymentNotifyResult.fail(CHANNEL_EASYPAY, "EasyPay商户不匹配");
        String content = PaymentSignUtil.canonicalQuery(params, Set.of("sign", "sign_type"), false) + require(config.getSecretKey(), "EasyPay密钥");
        String expected = PaymentSignUtil.md5Lower(content);
        if (!PaymentSignUtil.secureEquals(expected.toLowerCase(), defaultText(params.get("sign"), "").toLowerCase())) {
            return PaymentNotifyResult.fail(CHANNEL_EASYPAY, "EasyPay签名校验失败");
        }
        return PaymentNotifyResult.ok(CHANNEL_EASYPAY, params.get("out_trade_no"), params.get("trade_no"), amount(params.get("money")), new HashMap<>(params));
    }

    private PaymentNotifyResult verifyAlipay(PaymentConfig config, Map<String, String> params) {
        String status = params.get("trade_status");
        if (!"TRADE_SUCCESS".equals(status) && !"TRADE_FINISHED".equals(status)) return PaymentNotifyResult.fail(CHANNEL_ALIPAY, "支付宝交易未成功");
        if (!Objects.equals(params.get("app_id"), require(config.getAppId(), "支付宝App ID"))) return PaymentNotifyResult.fail(CHANNEL_ALIPAY, "支付宝应用不匹配");
        String content = PaymentSignUtil.canonicalQuery(params, Set.of("sign", "sign_type"), false);
        if (!PaymentSignUtil.rsaSha256Verify(content, params.get("sign"), require(config.getPublicKey(), "支付宝公钥"))) {
            return PaymentNotifyResult.fail(CHANNEL_ALIPAY, "支付宝签名校验失败");
        }
        return PaymentNotifyResult.ok(CHANNEL_ALIPAY, params.get("out_trade_no"), params.get("trade_no"), amount(params.get("total_amount")), new HashMap<>(params));
    }

    private PaymentNotifyResult verifyWechat(PaymentConfig config, String body, Map<String, String> headers) {
        String timestamp = header(headers, "Wechatpay-Timestamp");
        String nonce = header(headers, "Wechatpay-Nonce");
        String signature = header(headers, "Wechatpay-Signature");
        String message = timestamp + "\n" + nonce + "\n" + body + "\n";
        if (!PaymentSignUtil.rsaSha256Verify(message, signature, require(config.getPublicKey(), "微信平台证书/公钥"))) {
            return PaymentNotifyResult.fail(CHANNEL_WECHAT, "微信支付签名校验失败");
        }
        JSONObject payload = JSON.parseObject(body);
        JSONObject resource = payload.getJSONObject("resource");
        String plain = PaymentSignUtil.aesGcmDecrypt(require(config.getSecretKey(), "微信APIv3密钥"), resource.getString("nonce"), resource.getString("associated_data"), resource.getString("ciphertext"));
        JSONObject data = JSON.parseObject(plain);
        if (!"SUCCESS".equals(data.getString("trade_state"))) return PaymentNotifyResult.fail(CHANNEL_WECHAT, "微信支付交易未成功");
        if (!Objects.equals(data.getString("appid"), require(config.getAppId(), "微信App ID"))) return PaymentNotifyResult.fail(CHANNEL_WECHAT, "微信应用不匹配");
        if (!Objects.equals(data.getString("mchid"), require(config.getMerchantId(), "微信商户号"))) return PaymentNotifyResult.fail(CHANNEL_WECHAT, "微信商户不匹配");
        JSONObject amount = data.getJSONObject("amount");
        BigDecimal paid = PaymentSignUtil.centsToAmount(amount == null ? 0 : amount.getInteger("payer_total"));
        return PaymentNotifyResult.ok(CHANNEL_WECHAT, data.getString("out_trade_no"), data.getString("transaction_id"), paid, data);
    }

    private PaymentNotifyResult verifyStripe(PaymentConfig config, String body, Map<String, String> headers) {
        String signatureHeader = header(headers, "Stripe-Signature");
        String timestamp = null;
        String signature = null;
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) continue;
            if ("t".equals(pair[0])) timestamp = pair[1];
            if ("v1".equals(pair[0])) signature = pair[1];
        }
        String signedPayload = timestamp + "." + body;
        String expected = PaymentSignUtil.hmacSha256Hex(require(config.getEndpointSecret(), "Stripe Webhook Secret"), signedPayload);
        if (!PaymentSignUtil.secureEquals(expected, signature)) return PaymentNotifyResult.fail(CHANNEL_STRIPE, "Stripe签名校验失败");
        JSONObject event = JSON.parseObject(body);
        String type = event.getString("type");
        if (!"checkout.session.completed".equals(type) && !"payment_intent.succeeded".equals(type)) {
            return PaymentNotifyResult.fail(CHANNEL_STRIPE, "Stripe事件不是支付成功");
        }
        JSONObject object = event.getJSONObject("data").getJSONObject("object");
        JSONObject metadata = object.getJSONObject("metadata");
        String orderNo = defaultText(object.getString("client_reference_id"), metadata == null ? null : metadata.getString("order_no"));
        String providerTradeNo = defaultText(object.getString("payment_intent"), object.getString("id"));
        Integer cents = object.getInteger("amount_total");
        if (cents == null) cents = object.getInteger("amount_received");
        return PaymentNotifyResult.ok(CHANNEL_STRIPE, orderNo, providerTradeNo, PaymentSignUtil.centsToAmount(cents), object);
    }

    private String wechatAuthorization(PaymentConfig config, String gateway, String body) throws Exception {
        URL url = new URL(gateway);
        String path = StringUtils.hasText(url.getQuery()) ? url.getPath() + "?" + url.getQuery() : url.getPath();
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = PaymentSignUtil.randomNonce();
        String message = "POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
        String sign = PaymentSignUtil.rsaSha256Sign(message, require(config.getPrivateKey(), "微信商户私钥"));
        return "WECHATPAY2-SHA256-RSA2048 mchid=\"" + require(config.getMerchantId(), "微信商户号")
                + "\",nonce_str=\"" + nonce + "\",signature=\"" + sign
                + "\",timestamp=\"" + timestamp + "\",serial_no=\"" + require(config.getSerialNo(), "微信商户证书序列号") + "\"";
    }

    private PaymentConfig findConfig(String channel) {
        QueryWrapper<PaymentConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("channel", normalizeChannel(channel));
        return paymentConfigMapper.selectOne(wrapper);
    }

    private PaymentConfig getOrDefaultConfig(String channel) {
        PaymentConfig config = findConfig(channel);
        if (config != null) return config;
        PaymentConfig fallback = new PaymentConfig();
        fallback.setChannel(channel);
        fallback.setDisplayName(defaultDisplayName(channel));
        fallback.setEnabled(false);
        fallback.setPayType(defaultPayType(channel));
        fallback.setGatewayUrl(defaultGateway(channel));
        fallback.setCurrency(defaultCurrency(channel));
        fallback.setStatus(ACTIVE);
        return fallback;
    }

    private Map<String, Object> toUserView(PaymentConfig config) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("channel", config.getChannel());
        view.put("displayName", config.getDisplayName());
        view.put("enabled", Boolean.TRUE.equals(config.getEnabled()) && Objects.equals(config.getStatus(), ACTIVE));
        view.put("payType", config.getPayType());
        view.put("currency", config.getCurrency());
        return view;
    }

    private Map<String, Object> toAdminView(PaymentConfig config) {
        Map<String, Object> view = toUserView(config);
        view.put("gatewayUrl", config.getGatewayUrl());
        view.put("appId", config.getAppId());
        view.put("merchantId", config.getMerchantId());
        view.put("secretKey", mask(config.getSecretKey()));
        view.put("privateKey", mask(config.getPrivateKey()));
        view.put("publicKey", mask(config.getPublicKey()));
        view.put("apiKey", mask(config.getApiKey()));
        view.put("endpointSecret", mask(config.getEndpointSecret()));
        view.put("serialNo", config.getSerialNo());
        view.put("notifyUrl", config.getNotifyUrl());
        view.put("returnUrl", config.getReturnUrl());
        view.put("cancelUrl", config.getCancelUrl());
        view.put("status", config.getStatus());
        return view;
    }

    private List<String> orderedChannels() {
        return List.of(CHANNEL_EASYPAY, CHANNEL_ALIPAY, CHANNEL_WECHAT, CHANNEL_STRIPE);
    }

    private String header(Map<String, String> headers, String name) {
        if (headers == null) return "";
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) return defaultText(entry.getValue(), "");
        }
        return "";
    }

    private String mergeSecret(String oldValue, String newValue) {
        if (!StringUtils.hasText(newValue) || isMasked(newValue)) return oldValue;
        return newValue.trim();
    }

    private boolean isMasked(String value) {
        return value != null && value.contains("****");
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value)) return "";
        String trimmed = value.trim();
        if (trimmed.length() <= 8) return "****";
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private String defaultGateway(String channel) {
        if (CHANNEL_ALIPAY.equals(channel)) return "https://openapi.alipay.com/gateway.do";
        if (CHANNEL_WECHAT.equals(channel)) return "https://api.mch.weixin.qq.com/v3/pay/transactions/native";
        if (CHANNEL_STRIPE.equals(channel)) return "https://api.stripe.com/v1/checkout/sessions";
        return "";
    }

    private String defaultDisplayName(String channel) {
        if (CHANNEL_EASYPAY.equals(channel)) return "EasyPay 易支付";
        if (CHANNEL_ALIPAY.equals(channel)) return "支付宝官方";
        if (CHANNEL_WECHAT.equals(channel)) return "微信支付官方";
        if (CHANNEL_STRIPE.equals(channel)) return "Stripe";
        return channel;
    }

    private String defaultPayType(String channel) {
        return CHANNEL_EASYPAY.equals(channel) ? "alipay" : "";
    }

    private String defaultCurrency(String channel) {
        return CHANNEL_STRIPE.equals(channel) ? "cny" : "CNY";
    }

    private String normalizeChannel(String channel) {
        return channel == null ? "" : channel.trim().toLowerCase();
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String require(String value, String label) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(label + "未配置");
        return value.trim();
    }

    private String trimSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private String trimQuestion(String value) {
        return value.endsWith("?") ? value.substring(0, value.length() - 1) : value;
    }

    private String amountText(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal amount(String amount) {
        if (!StringUtils.hasText(amount)) return BigDecimal.ZERO;
        return new BigDecimal(amount.trim()).setScale(2, RoundingMode.HALF_UP);
    }

    private long amountToCents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
