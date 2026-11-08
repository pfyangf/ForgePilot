package com.forgepilot.system.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CodexProviderEligibilityTest {

    @Test
    void responsesProviderIsEligibleWhenEnabled() {
        assertThat(CodexProviderEligibility.eligibleForCodex(true, UpstreamWireApi.RESPONSES, null)).isTrue();
        assertThat(CodexProviderEligibility.needsTranslationGateway(UpstreamWireApi.RESPONSES, null)).isFalse();
    }

    @Test
    void chatOrNativeProviderNeedsGatewayBeforeCodexCanUseIt() {
        assertThat(CodexProviderEligibility.needsTranslationGateway(UpstreamWireApi.CHAT_COMPLETIONS, null)).isTrue();
        assertThat(CodexProviderEligibility.needsTranslationGateway(UpstreamWireApi.NATIVE, "  ")).isTrue();
        assertThat(CodexProviderEligibility.eligibleForCodex(true, UpstreamWireApi.CHAT_COMPLETIONS, null)).isFalse();
        assertThat(CodexProviderEligibility.eligibleForCodex(true, UpstreamWireApi.NATIVE, null)).isFalse();
    }

    @Test
    void configuredGatewayMakesNonResponsesProviderEligible() {
        assertThat(CodexProviderEligibility.eligibleForCodex(true, UpstreamWireApi.CHAT_COMPLETIONS, "litellm"))
                .isTrue();
        assertThat(CodexProviderEligibility.eligibleForCodex(true, UpstreamWireApi.NATIVE, "litellm")).isTrue();
    }

    @Test
    void disabledProviderIsNotEligible() {
        assertThat(CodexProviderEligibility.eligibleForCodex(false, UpstreamWireApi.RESPONSES, null)).isFalse();
    }
}
