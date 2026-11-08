package com.forgepilot.system.model;

public enum UpstreamWireApi {
    RESPONSES,
    CHAT_COMPLETIONS,
    NATIVE;

    public static UpstreamWireApi parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("缺少上游协议");
        }
        return switch (value) {
            case "responses" -> RESPONSES;
            case "chat_completions" -> CHAT_COMPLETIONS;
            case "native" -> NATIVE;
            default -> throw new IllegalArgumentException("未知上游协议: " + value);
        };
    }
}
