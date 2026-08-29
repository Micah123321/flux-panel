package com.admin.common.utils;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PaymentSignUtil {
    private PaymentSignUtil() {
    }

    public static String md5Lower(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) builder.append(String.format("%02x", item));
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5签名失败", e);
        }
    }

    public static String hmacSha256Hex(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) builder.append(String.format("%02x", item));
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC签名失败", e);
        }
    }

    public static boolean secureEquals(String left, String right) {
        if (left == null || right == null) return false;
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(leftBytes, rightBytes);
    }

    public static String canonicalQuery(Map<String, String> params, Set<String> excludeKeys, boolean encodeValue) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        List<String> pairs = new ArrayList<>();
        for (String key : keys) {
            String value = params.get(key);
            if (excludeKeys.contains(key) || value == null || value.isEmpty()) continue;
            pairs.add(key + "=" + (encodeValue ? urlEncode(value) : value));
        }
        return String.join("&", pairs);
    }

    public static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception e) {
            throw new IllegalArgumentException("URL编码失败", e);
        }
    }

    public static String rsaSha256Sign(String content, String privateKeyPem) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(loadPrivateKey(privateKeyPem));
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("RSA2签名失败", e);
        }
    }

    public static boolean rsaSha256Verify(String content, String sign, String publicKeyPem) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(loadPublicKey(publicKeyPem));
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            return false;
        }
    }

    public static String aesGcmDecrypt(String apiV3Key, String nonce, String associatedData, String ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec key = new SecretKeySpec(apiV3Key.getBytes(StandardCharsets.UTF_8), "AES");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
            if (associatedData != null && !associatedData.isEmpty()) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("微信支付回调解密失败", e);
        }
    }

    public static String randomNonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    public static BigDecimal centsToAmount(Integer cents) {
        if (cents == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
    }

    private static PrivateKey loadPrivateKey(String privateKeyPem) throws Exception {
        String key = cleanPem(privateKeyPem);
        byte[] bytes = Base64.getDecoder().decode(key);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    private static PublicKey loadPublicKey(String publicKeyPem) throws Exception {
        if (publicKeyPem.contains("BEGIN CERTIFICATE")) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return factory.generateCertificate(new java.io.ByteArrayInputStream(publicKeyPem.getBytes(StandardCharsets.UTF_8))).getPublicKey();
        }
        String key = cleanPem(publicKeyPem);
        byte[] bytes = Base64.getDecoder().decode(key);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
    }

    private static String cleanPem(String pem) {
        if (pem == null) throw new IllegalArgumentException("密钥不能为空");
        return pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
    }
}
