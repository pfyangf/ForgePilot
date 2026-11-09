package com.forgepilot.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.execution.persistence.AgentProfileMapper;
import com.forgepilot.execution.persistence.AgentSessionMapper;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.domain.IntegrationSecret;
import com.forgepilot.system.domain.ModelProvider;
import com.forgepilot.system.persistence.IntegrationSecretMapper;
import com.forgepilot.system.persistence.ModelProviderMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ModelProviderCatalogServiceTest {

    private final ModelProviderMapper modelProviderMapper = mock(ModelProviderMapper.class);
    private final IntegrationSecretMapper secretMapper = mock(IntegrationSecretMapper.class);
    private final AgentProfileMapper agentProfileMapper = mock(AgentProfileMapper.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final CodingTaskMapper codingTaskMapper = mock(CodingTaskMapper.class);
    private final AgentSessionMapper agentSessionMapper = mock(AgentSessionMapper.class);
    private final ActivityLogRecorder recorder = mock(ActivityLogRecorder.class);
    private ModelProviderCatalogService service;

    @BeforeEach
    void setUp() {
        service = new ModelProviderCatalogService(
                modelProviderMapper,
                secretMapper,
                agentProfileMapper,
                projectMapper,
                codingTaskMapper,
                agentSessionMapper,
                new RoleAccess(),
                recorder,
                new ObjectMapper());
        when(modelProviderMapper.insert(any(ModelProvider.class))).thenReturn(1);
        when(secretMapper.insert(any(IntegrationSecret.class))).thenReturn(1);
        when(agentProfileMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(projectMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(codingTaskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(agentSessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
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
                        "sk-live-should-mask",
                        List.of(new ModelProviderCatalogService.ModelCode("gpt-4o", "GPT-4o"))));

        assertThat(view.hasSecret()).isTrue();
        assertThat(view.eligibleForCodex()).isTrue();
        assertThat(view.models()).containsExactly(new ModelProviderCatalogService.ModelCode("gpt-4o", "GPT-4o"));
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
        provider.setExtraConfig(Map.of(
                "models", List.of(Map.of("code", "deepseek-chat", "name", "DeepSeek Chat"))));
        when(modelProviderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(provider));

        AuthUser admin = new AuthUser("a1", "demo_admin", "管理员", "t1", List.of("TENANT_ADMIN"), List.of());
        var list = service.list(admin);
        assertThat(list.get(0).eligibleForCodex()).isFalse();
        assertThat(list.get(0).needsTranslationGateway()).isTrue();
        assertThat(list.get(0).models())
                .containsExactly(new ModelProviderCatalogService.ModelCode("deepseek-chat", "DeepSeek Chat"));
        assertThat(list.get(0).toString()).doesNotContain("ciphertext").doesNotContain("secretId=");
    }

    @Test
    void deleteBlockedWhenReferencedByCodingTool() {
        ModelProvider provider = ownedProvider();
        when(modelProviderMapper.selectById("p1")).thenReturn(provider);
        when(agentProfileMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        AuthUser admin = new AuthUser("a1", "demo_admin", "管理员", "t1", List.of("TENANT_ADMIN"), List.of());
        assertThatThrownBy(() -> service.delete(admin, "p1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("编码工具");
                });
    }

    @Test
    void deleteRemovesProviderAndSecret() {
        ModelProvider provider = ownedProvider();
        when(modelProviderMapper.selectById("p1")).thenReturn(provider);
        when(modelProviderMapper.deleteById("p1")).thenReturn(1);
        when(secretMapper.deleteById("s1")).thenReturn(1);

        AuthUser admin = new AuthUser("a1", "demo_admin", "管理员", "t1", List.of("TENANT_ADMIN"), List.of());
        service.delete(admin, "p1");

        verify(modelProviderMapper).deleteById("p1");
        verify(secretMapper).deleteById("s1");
        verify(recorder)
                .record(
                        eq("t1"),
                        eq("USER"),
                        eq("a1"),
                        eq("MODEL_PROVIDER_DELETE"),
                        eq("MODEL_PROVIDER"),
                        eq("p1"),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq("SUCCESS"));
    }

    @Test
    void modelsRoundTripOnUpdate() {
        ModelProvider provider = ownedProvider();
        when(modelProviderMapper.selectById("p1")).thenReturn(provider);
        when(modelProviderMapper.updateById(any(ModelProvider.class))).thenReturn(1);

        AuthUser admin = new AuthUser("a1", "demo_admin", "管理员", "t1", List.of("TENANT_ADMIN"), List.of());
        var view = service.update(
                admin,
                "p1",
                new ModelProviderCatalogService.UpsertRequest(
                        "chat",
                        "OPENAI_COMPAT",
                        "http://x",
                        true,
                        null,
                        "chat_completions",
                        null,
                        null,
                        List.of(
                                new ModelProviderCatalogService.ModelCode("gpt-4o", "GPT-4o"),
                                new ModelProviderCatalogService.ModelCode("gpt-4o-mini", null))));

        assertThat(view.models())
                .containsExactly(
                        new ModelProviderCatalogService.ModelCode("gpt-4o", "GPT-4o"),
                        new ModelProviderCatalogService.ModelCode("gpt-4o-mini", null));
        ArgumentCaptor<ModelProvider> captor = ArgumentCaptor.forClass(ModelProvider.class);
        verify(modelProviderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getExtraConfig()).containsKey("models");
    }


    @Test
    void testRecordsFailureResultWhenUpstreamUnreachable() {
        ModelProvider provider = ownedProvider();
        provider.setBaseUrl("http://127.0.0.1:9/v1");
        provider.setUpstreamWireApi("responses");
        when(modelProviderMapper.selectById("p1")).thenReturn(provider);
        IntegrationSecret secret = new IntegrationSecret();
        secret.setId("s1");
        secret.setCiphertext("sk-test".getBytes());
        when(secretMapper.selectById("s1")).thenReturn(secret);

        AuthUser admin = new AuthUser("a1", "demo_admin", "管理员", "t1", List.of("TENANT_ADMIN"), List.of());
        var result = service.test(admin, "p1", "gpt-4o");

        assertThat(result.ok()).isFalse();
        verify(recorder)
                .record(
                        eq("t1"),
                        eq("USER"),
                        eq("a1"),
                        eq("MODEL_PROVIDER_TEST"),
                        eq("MODEL_PROVIDER"),
                        eq("p1"),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq("FAILURE"));
    }

    @Test
    void testRequiresSecret() {
        ModelProvider provider = ownedProvider();
        provider.setSecretId(null);
        when(modelProviderMapper.selectById("p1")).thenReturn(provider);

        AuthUser admin = new AuthUser("a1", "demo_admin", "管理员", "t1", List.of("TENANT_ADMIN"), List.of());
        assertThatThrownBy(() -> service.test(admin, "p1", "gpt-4o"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("API Key");
                });
    }

    private static ModelProvider ownedProvider() {
        ModelProvider provider = new ModelProvider();
        provider.setId("p1");
        provider.setTenantId("t1");
        provider.setName("chat");
        provider.setProviderType("OPENAI_COMPAT");
        provider.setBaseUrl("http://x");
        provider.setEnabled(true);
        provider.setUpstreamWireApi("chat_completions");
        provider.setSecretId("s1");
        provider.setExtraConfig(Map.of());
        return provider;
    }
}
