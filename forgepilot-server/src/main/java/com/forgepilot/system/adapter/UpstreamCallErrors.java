package com.forgepilot.system.adapter;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/** User-facing upstream call errors. Never include API keys or other secrets. */
public final class UpstreamCallErrors {

    private UpstreamCallErrors() {}

    public static String modelCallFailed(String baseUrl, Throwable error) {
        return "模型调用失败：" + describe(baseUrl, error);
    }

    public static String probeFailed(String baseUrl, Throwable error) {
        return "接口测试失败：" + describe(baseUrl, error);
    }

    public static String describe(String baseUrl, Throwable error) {
        String target = describeTarget(baseUrl);
        String reason = describeReason(error);
        if (target.isBlank()) {
            return reason;
        }
        return "无法连接 " + target + "（" + reason + "）";
    }

    public static String describeTarget(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "";
            }
            int port = uri.getPort();
            if (port < 0) {
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                port = "https".equals(scheme) ? 443 : 80;
            }
            return host + ":" + port;
        } catch (Exception ex) {
            return "";
        }
    }

    public static String describeReason(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ConnectException) {
                String message = current.getMessage();
                if (message != null && message.toLowerCase(Locale.ROOT).contains("unreachable")) {
                    return "网络不可达，可尝试强制使用 IPv4";
                }
                if (message != null && (message.toLowerCase(Locale.ROOT).contains("refused")
                        || message.toLowerCase(Locale.ROOT).contains("connection refused"))) {
                    return "连接被拒绝，请确认该地址有服务在监听";
                }
                return "无法建立连接";
            }
            if (current instanceof UnknownHostException) {
                return "无法解析主机名";
            }
            if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException) {
                return "连接超时";
            }
            current = current.getCause();
        }
        String message = rootMessage(error);
        if (message == null || message.isBlank()) {
            return error == null ? "未知错误" : error.getClass().getSimpleName();
        }
        return sanitize(message);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        String last = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                last = current.getMessage();
            }
            current = current.getCause();
        }
        return last;
    }

    static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        String scrubbed = message.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._\\-]+", "Bearer ***");
        scrubbed = scrubbed.replaceAll("(?i)sk-[A-Za-z0-9]{8,}", "sk-***");
        scrubbed = scrubbed.replaceAll("(?i)(api[_-]?key|token)\\s*[=:]\\s*\\S+", "$1=***");
        return scrubbed.length() <= 200 ? scrubbed : scrubbed.substring(0, 200);
    }
}
