package com.forgepilot.system.activity;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 操作记录的结构化载荷在落库前去掉密钥。调用方仍不应把明文密钥放进摘要。 */
public final class ActivityPayloadMasker {

    private static final Set<String> SECRET_KEYS = Set.of(
            "password",
            "passwordhash",
            "token",
            "githubtoken",
            "gitlabtoken",
            "accesstoken",
            "secret",
            "apikey",
            "api_key",
            "authorization",
            "ciphertext");

    private ActivityPayloadMasker() {
    }

    public static Map<String, Object> mask(Map<String, Object> payload) {
        Map<String, Object> masked = new LinkedHashMap<>();
        if (payload == null) {
            return masked;
        }
        payload.forEach((key, value) -> masked.put(key, maskValue(key, value)));
        return masked;
    }

    @SuppressWarnings("unchecked")
    private static Object maskValue(String key, Object value) {
        if (isSecret(key)) {
            return "***";
        }
        if (value instanceof Map<?, ?> nested) {
            return mask((Map<String, Object>) nested);
        }
        return value;
    }

    private static boolean isSecret(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT).replace("-", "");
        return SECRET_KEYS.contains(normalized);
    }
}
