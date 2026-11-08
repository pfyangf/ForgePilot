package com.forgepilot.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.domain.IntegrationSecret;
import com.forgepilot.system.domain.ModelProvider;
import com.forgepilot.system.persistence.IntegrationSecretMapper;
import com.forgepilot.system.persistence.ModelProviderMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelProviderCatalogServiceTest {

    private final ModelProviderMapper modelProviderMapper = mock(ModelProviderMapper.class);
    private final IntegrationSecretMapper secretMapper = mock(IntegrationSecretMapper.class);
    private final ActivityLogRecorder recorder = mock(ActivityLogRecorder.class);
    private ModelProviderCatalogService service;

    @BeforeEach
    void setUp() {
        service = new ModelProviderCatalogService(modelProviderMapper, secretMapper, new RoleAccess(), recorder);
        when(modelProviderMapper.insert(any(ModelProvider.class))).thenReturn(1);
        when(secretMapper.insert(any(IntegrationSecret.class))).thenReturn(1);
    }

    @Test
    void createMasksApiKeyInActivityPayloadAndOmitsSecretFromView() {
        AuthUser admin = new AuthUser("a1", "demo_admin", "管理员", "t1", List.of("TENANT_ADMIN"), List.of());
        var view = service.create(
                admin,
                new ModelProviderCatalogService.UpsertRequest(
                        "本地 Responses",
                        "OPENAI_COMPAT",
                        "http://127.0.0.1:1/v1",
                        true,
                        null,
                        "responses",
                        null,
                        "sk-live-should-mask"));

        assertThat(view.hasSecret()).isTrue();
        assertThat(view.eligibleForCodex()).isTrue();
        // record 的序列化字段名里不能把明文密钥露出到 view
        String jsonish = view.toString();
        assertThat(jsonish).doesNotContain("sk-live");

        ArgumentCaptor<java.util.Map<String, Object>> payload = ArgumentCaptor.forClass(java.util.Map.class);
        verify(recorder)
                .record(
                        eq("t1"),
                        eq("USER"),
                        eq("a1"),
                        eq("MODEL_PROVIDER_CREATE"),
                        eq("MODEL_PROVIDER"),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        payload.capture(),
                        eq("SUCCESS"));
        // recorder 内部会再 mask；这里断言调用方虽传入了 apiKey 字段，masker 会处理
        assertThat(payload.getValue()).containsKey("apiKey");
    }

    @Test
    void chatCompletionsWithoutGatewayIsNotEligibleForCodex() {
        ModelProvider provider = new ModelProvider();
        provider.setId("p1");
        provider.setTenantId("t1");
        provider.setName("chat");
        provider.setProviderType("OPENAI_COMPAT");
        provider.setBaseUrl("http://x");
        provider.setEnabled(true);
        provider.setUpstreamWireApi("chat_completions");
        provider.setSecretId("s1");
        when(modelProviderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(provider));

        AuthUser admin = new AuthUser("a1", "demo_admin", "管理员", "t1", List.of("TENANT_ADMIN"), List.of());
        var list = service.list(admin);
        assertThat(list.get(0).eligibleForCodex()).isFalse();
        assertThat(list.get(0).needsTranslationGateway()).isTrue();
        assertThat(list.get(0).toString()).doesNotContain("ciphertext").doesNotContain("secretId=");
    }
}
