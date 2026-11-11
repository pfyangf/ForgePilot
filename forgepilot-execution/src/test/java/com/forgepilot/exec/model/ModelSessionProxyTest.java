package com.forgepilot.exec.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgepilot.exec.model.InMemoryModelSessionProxy.UnauthorizedProxyException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelSessionProxyTest {

    @Test
    void issuesResponsesOnlyCredentialWithoutRawApiKeyInAgentConfig() {
        InMemoryModelSessionProxy proxy = new InMemoryModelSessionProxy("127.0.0.1", 19081, 3600);
        var cred = proxy.issue(new ModelSessionProxy.ModelSessionRequest(
                "s1", "prov", "gpt", "https://api.openai.com/v1", "sk-secret-provider-key", "responses"));
        assertThat(proxy.exposedWireApi()).isEqualTo("responses");
        assertThat(cred.baseUrl()).isEqualTo("http://127.0.0.1:19081/v1");
        assertThat(cred.shortLivedToken()).startsWith("fp_ms_");
        Map<String, Object> agentConfig = proxy.agentFacingConfig(cred);
        assertThat(agentConfig.get("wire_api")).isEqualTo("responses");
        assertThat(agentConfig.toString()).doesNotContain("sk-secret-provider-key");
        assertThat(agentConfig.containsKey("apiKey")).isFalse();
    }

    @Test
    void rejectsMissingAndRevokedTokens() {
        InMemoryModelSessionProxy proxy = new InMemoryModelSessionProxy("127.0.0.1", 19081, 3600);
        assertThatThrownBy(() -> proxy.requireValidToken(null))
                .isInstanceOf(UnauthorizedProxyException.class);
        assertThatThrownBy(() -> proxy.requireValidToken("Bearer missing"))
                .isInstanceOf(UnauthorizedProxyException.class);

        var cred = proxy.issue(new ModelSessionProxy.ModelSessionRequest(
                "s2", "prov", "m", "http://up", "key", "responses"));
        assertThat(proxy.requireValidToken("Bearer " + cred.shortLivedToken()).sessionId()).isEqualTo("s2");
        proxy.revoke("s2");
        assertThatThrownBy(() -> proxy.requireValidToken("Bearer " + cred.shortLivedToken()))
                .isInstanceOf(UnauthorizedProxyException.class)
                .hasMessageContaining("撤销");
    }

    @Test
    void rejectsExpiredToken() {
        InMemoryModelSessionProxy proxy = new InMemoryModelSessionProxy("127.0.0.1", 19081, 0);
        var cred = proxy.issue(new ModelSessionProxy.ModelSessionRequest(
                "s3", "prov", "m", "http://up", "key", "responses"));
        try {
            Thread.sleep(20);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        assertThatThrownBy(() -> proxy.requireValidToken(cred.shortLivedToken()))
                .isInstanceOf(UnauthorizedProxyException.class)
                .hasMessageContaining("过期");
    }
}
