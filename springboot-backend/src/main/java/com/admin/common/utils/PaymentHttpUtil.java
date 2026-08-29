package com.admin.common.utils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PaymentHttpUtil {
    private PaymentHttpUtil() {
    }

    public static String postJson(String url, String body, Map<String, String> headers) throws Exception {
        return post(url, body, headers);
    }

    public static String postForm(String url, Map<String, String> form, Map<String, String> headers) throws Exception {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            pairs.add(PaymentSignUtil.urlEncode(entry.getKey()) + "=" + PaymentSignUtil.urlEncode(entry.getValue()));
        }
        return post(url, String.join("&", pairs), headers);
    }

    public static String readUtf8(InputStream input) throws Exception {
        if (input == null) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static String post(String url, String body, Map<String, String> headers) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setDoOutput(true);
        for (Map.Entry<String, String> entry : headers.entrySet()) connection.setRequestProperty(entry.getKey(), entry.getValue());
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Length", String.valueOf(payload.length));
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload);
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = readUtf8(stream);
        if (code < 200 || code >= 300) throw new IllegalStateException(response);
        return response;
    }
}
