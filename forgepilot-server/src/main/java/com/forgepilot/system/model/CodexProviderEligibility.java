package com.forgepilot.system.model;

/**
 * Codex CLI 只说 Responses。上游不是 Responses 时，必须已经配置翻译网关，否则不能给 Codex 使用。
 * 目录里仍可以保存未配网关的记录，启动任务时拒绝。
 */
public final class CodexProviderEligibility {

    private CodexProviderEligibility() {
    }

    public static boolean needsTranslationGateway(UpstreamWireApi upstream, String translationGateway) {
        return upstream != UpstreamWireApi.RESPONSES && isBlank(translationGateway);
    }

    public static boolean eligibleForCodex(boolean enabled, UpstreamWireApi upstream, String translationGateway) {
        return enabled && !needsTranslationGateway(upstream, translationGateway);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
