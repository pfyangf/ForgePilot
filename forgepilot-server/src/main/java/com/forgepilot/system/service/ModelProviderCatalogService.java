package com.forgepilot.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.execution.domain.AgentProfile;
import com.forgepilot.execution.domain.AgentSession;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.persistence.AgentProfileMapper;
import com.forgepilot.execution.persistence.AgentSessionMapper;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.domain.IntegrationSecret;
import com.forgepilot.system.domain.ModelProvider;
import com.forgepilot.system.model.CodexProviderEligibility;
import com.forgepilot.system.model.UpstreamWireApi;
import com.forgepilot.system.persistence.IntegrationSecretMapper;
import com.forgepilot.system.persistence.ModelProviderMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModelProviderCatalogService {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int DETAIL_LIMIT = 500;

    private final ModelProviderMapper modelProviderMapper;
    private final IntegrationSecretMapper integrationSecretMapper;
    private final AgentProfileMapper agentProfileMapper;
    private final ProjectMapper projectMapper;
    private final CodingTaskMapper codingTaskMapper;
    private final AgentSessionMapper agentSessionMapper;
    private final RoleAccess roleAccess;
    private final ActivityLogRecorder activityLogRecorder;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(TEST_TIMEOUT).build();

    public ModelProviderCatalogService(
            ModelProviderMapper modelProviderMapper,
            IntegrationSecretMapper integrationSecretMapper,
            AgentProfileMapper agentProfileMapper,
            ProjectMapper projectMapper,
            CodingTaskMapper codingTaskMapper,
            AgentSessionMapper agentSessionMapper,
            RoleAccess roleAccess,
            ActivityLogRecorder activityLogRecorder,
            ObjectMapper objectMapper) {
        this.modelProviderMapper = modelProviderMapper;
        this.integrationSecretMapper = integrationSecretMapper;
        this.agentProfileMapper = agentProfileMapper;
        this.projectMapper = projectMapper;
        this.codingTaskMapper = codingTaskMapper;
        this.agentSessionMapper = agentSessionMapper;
        this.roleAccess = roleAccess;
        this.activityLogRecorder = activityLogRecorder;
        this.objectMapper = objectMapper;
    }

    public List<ProviderView> list(AuthUser user) {
        roleAccess.requireTenantAdmin(user);
        return modelProviderMapper
                .selectList(new LambdaQueryWrapper<ModelProvider>()
                        .eq(ModelProvider::getTenantId, user.tenantId())
                        .orderByAsc(ModelProvider::getName))
                .stream()
                .map(this::toView)
                .toList();
    }

    public ProviderView get(AuthUser user, String id) {
        return toView(requireOwned(user, id));
    }

    @Transactional
    public ProviderView create(AuthUser user, UpsertRequest request) {
        roleAccess.requireTenantAdmin(user);
        UpstreamWireApi wire = parseWire(request.upstreamWireApi());
        String providerId = UUID.randomUUID().toString();
        String secretId = null;
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            secretId = storeSecret(user, providerId, request.apiKey());
        }
        ModelProvider provider = new ModelProvider();
        provider.setId(providerId);
        provider.setTenantId(user.tenantId());
        apply(provider, request, wire);
        provider.setSecretId(secretId);
        provider.setCreatedBy(user.userId());
        modelProviderMapper.insert(provider);
        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "MODEL_PROVIDER_CREATE",
                "MODEL_PROVIDER",
                providerId,
                null,
                null,
                null,
                null,
                "创建模型 Provider：" + provider.getName(),
                Map.of(
                        "name", provider.getName(),
                        "providerType", provider.getProviderType(),
                        "upstreamWireApi", provider.getUpstreamWireApi(),
                        "hasSecret", secretId != null,
                        "modelCount", readModels(provider).size(),
                        "apiKey", request.apiKey() == null ? "" : request.apiKey()),
                "SUCCESS");
        return toView(provider);
    }

    @Transactional
    public ProviderView update(AuthUser user, String id, UpsertRequest request) {
        ModelProvider provider = requireOwned(user, id);
        UpstreamWireApi wire = parseWire(request.upstreamWireApi());
        apply(provider, request, wire);
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            if (provider.getSecretId() == null) {
                provider.setSecretId(storeSecret(user, provider.getId(), request.apiKey()));
            } else {
                IntegrationSecret secret = integrationSecretMapper.selectById(provider.getSecretId());
                if (secret == null) {
                    provider.setSecretId(storeSecret(user, provider.getId(), request.apiKey()));
                } else {
                    secret.setCiphertext(request.apiKey().getBytes(StandardCharsets.UTF_8));
                    secret.setUpdatedBy(user.userId());
                    integrationSecretMapper.updateById(secret);
                }
            }
        }
        provider.setUpdatedBy(user.userId());
        modelProviderMapper.updateById(provider);
        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "MODEL_PROVIDER_UPDATE",
                "MODEL_PROVIDER",
                id,
                null,
                null,
                null,
                null,
                "更新模型 Provider：" + provider.getName(),
                Map.of(
                        "name", provider.getName(),
                        "hasSecret", provider.getSecretId() != null,
                        "modelCount", readModels(provider).size(),
                        "secretRotated", request.apiKey() != null && !request.apiKey().isBlank()),
                "SUCCESS");
        return toView(provider);
    }

    @Transactional
    public void delete(AuthUser user, String id) {
        ModelProvider provider = requireOwned(user, id);
        List<String> blockers = referenceBlockers(id);
        if (!blockers.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "无法删除：仍被引用（" + String.join("、", blockers) + "）");
        }
        String secretId = provider.getSecretId();
        String name = provider.getName();
        modelProviderMapper.deleteById(id);
        if (secretId != null && !secretId.isBlank()) {
            integrationSecretMapper.deleteById(secretId);
        }
        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "MODEL_PROVIDER_DELETE",
                "MODEL_PROVIDER",
                id,
                null,
                null,
                null,
                null,
                "删除模型 Provider：" + name,
                Map.of("name", name),
                "SUCCESS");
    }

    public TestResult test(AuthUser user, String id, String modelCode) {
        ModelProvider provider = requireOwned(user, id);
        if (provider.getSecretId() == null || provider.getSecretId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未配置 API Key，无法做接口测试");
        }
        IntegrationSecret secret = integrationSecretMapper.selectById(provider.getSecretId());
        if (secret == null || secret.getCiphertext() == null || secret.getCiphertext().length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未配置 API Key，无法做接口测试");
        }
        String apiKey = new String(secret.getCiphertext(), StandardCharsets.UTF_8);
        List<ModelCode> models = readModels(provider);
        String chosen = blankToNull(modelCode);
        if (chosen == null && !models.isEmpty()) {
            chosen = models.get(0).code();
        }
        UpstreamWireApi wire = UpstreamWireApi.parse(provider.getUpstreamWireApi());
        long started = System.nanoTime();
        TestResult result;
        try {
            result = probe(provider.getBaseUrl(), wire, chosen, apiKey);
        } catch (Exception ex) {
            long latencyMs = (System.nanoTime() - started) / 1_000_000L;
            result = new TestResult(false, 0, latencyMs, "请求失败：" + safeMessage(ex), null);
        }
        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "MODEL_PROVIDER_TEST",
                "MODEL_PROVIDER",
                id,
                null,
                null,
                null,
                null,
                (result.ok() ? "接口测试成功：" : "接口测试失败：") + provider.getName(),
                Map.of(
                        "name", provider.getName(),
                        "ok", result.ok(),
                        "status", result.status(),
                        "latencyMs", result.latencyMs(),
                        "modelCode", chosen == null ? "" : chosen,
                        "message", result.message() == null ? "" : result.message()),
                result.ok() ? "SUCCESS" : "FAILURE");
        return result;
    }

    private TestResult probe(String baseUrl, UpstreamWireApi wire, String modelCode, String apiKey)
            throws Exception {
        String root = trimTrailingSlash(baseUrl);
        long started = System.nanoTime();
        HttpRequest.Builder builder;
        if (modelCode != null && !modelCode.isBlank()) {
            builder = switch (wire) {
                case CHAT_COMPLETIONS -> postJson(
                        root + "/chat/completions",
                        Map.of(
                                "model", modelCode,
                                "messages", List.of(Map.of("role", "user", "content", "ping")),
                                "max_tokens", 8));
                case RESPONSES -> postJson(
                        root + "/responses",
                        Map.of("model", modelCode, "input", "ping"));
                case NATIVE -> get(root + "/models");
            };
        } else {
            builder = get(root + "/models");
        }
        HttpRequest request = builder.header("Authorization", "Bearer " + apiKey)
                .timeout(TEST_TIMEOUT)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long latencyMs = (System.nanoTime() - started) / 1_000_000L;
        String body = response.body() == null ? "" : response.body();
        String detail = truncate(stripSecrets(body, apiKey), DETAIL_LIMIT);
        boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
        String message = ok ? "上游返回 HTTP " + response.statusCode() : "上游返回 HTTP " + response.statusCode();
        return new TestResult(ok, response.statusCode(), latencyMs, message, detail);
    }

    private HttpRequest.Builder postJson(String url, Map<String, Object> body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        return HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
    }

    private static HttpRequest.Builder get(String url) {
        return HttpRequest.newBuilder(URI.create(url)).GET();
    }

    private List<String> referenceBlockers(String providerId) {
        List<String> blockers = new ArrayList<>();
        Long profiles = agentProfileMapper.selectCount(new LambdaQueryWrapper<AgentProfile>()
                .eq(AgentProfile::getModelProviderId, providerId));
        if (profiles != null && profiles > 0) {
            blockers.add("编码工具 " + profiles + " 个");
        }
        Long projects = projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                .eq(Project::getDefaultModelProviderId, providerId));
        if (projects != null && projects > 0) {
            blockers.add("项目默认 " + projects + " 个");
        }
        Long tasks = codingTaskMapper.selectCount(new LambdaQueryWrapper<CodingTask>()
                .eq(CodingTask::getModelProviderId, providerId));
        if (tasks != null && tasks > 0) {
            blockers.add("任务 " + tasks + " 个");
        }
        Long sessions = agentSessionMapper.selectCount(new LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getModelProviderId, providerId));
        if (sessions != null && sessions > 0) {
            blockers.add("Agent Session " + sessions + " 个");
        }
        return blockers;
    }

    private ModelProvider requireOwned(AuthUser user, String id) {
        roleAccess.requireTenantAdmin(user);
        ModelProvider provider = modelProviderMapper.selectById(id);
        if (provider == null || !user.tenantId().equals(provider.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider 不存在或无权查看");
        }
        return provider;
    }

    private String storeSecret(AuthUser user, String providerId, String apiKey) {
        IntegrationSecret secret = new IntegrationSecret();
        secret.setId(UUID.randomUUID().toString());
        secret.setOwnerType("MODEL_PROVIDER");
        secret.setOwnerId(providerId);
        secret.setPurpose("MODEL_PROVIDER");
        secret.setCiphertext(apiKey.getBytes(StandardCharsets.UTF_8));
        secret.setKeyVersion(1);
        secret.setCreatedBy(user.userId());
        integrationSecretMapper.insert(secret);
        return secret.getId();
    }

    private void apply(ModelProvider provider, UpsertRequest request, UpstreamWireApi wire) {
        provider.setName(request.name().trim());
        provider.setProviderType(request.providerType().trim().toUpperCase(Locale.ROOT));
        provider.setBaseUrl(request.baseUrl().trim());
        provider.setEnabled(request.enabled() == null || request.enabled());
        provider.setApiVersion(blankToNull(request.apiVersion()));
        provider.setUpstreamWireApi(switch (wire) {
            case RESPONSES -> "responses";
            case CHAT_COMPLETIONS -> "chat_completions";
            case NATIVE -> "native";
        });
        provider.setTranslationGateway(blankToNull(request.translationGateway()));
        provider.setExtraConfig(writeModels(normalizeModels(request.models())));
    }

    private ProviderView toView(ModelProvider provider) {
        UpstreamWireApi wire = UpstreamWireApi.parse(provider.getUpstreamWireApi());
        boolean hasSecret = provider.getSecretId() != null && !provider.getSecretId().isBlank();
        boolean enabled = Boolean.TRUE.equals(provider.getEnabled());
        boolean gatewayConfigured =
                provider.getTranslationGateway() != null && !provider.getTranslationGateway().isBlank();
        return new ProviderView(
                provider.getId(),
                provider.getTenantId(),
                provider.getName(),
                provider.getProviderType(),
                provider.getBaseUrl(),
                enabled,
                provider.getApiVersion(),
                provider.getUpstreamWireApi(),
                provider.getTranslationGateway(),
                hasSecret,
                gatewayConfigured,
                CodexProviderEligibility.eligibleForCodex(enabled, wire, provider.getTranslationGateway()),
                CodexProviderEligibility.needsTranslationGateway(wire, provider.getTranslationGateway()),
                readModels(provider));
    }

    @SuppressWarnings("unchecked")
    private List<ModelCode> readModels(ModelProvider provider) {
        Map<String, Object> extra = provider.getExtraConfig();
        if (extra == null || extra.isEmpty()) {
            return List.of();
        }
        Object raw = extra.get("models");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<ModelCode> models = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object code = map.get("code");
                if (code == null || code.toString().isBlank()) {
                    continue;
                }
                Object name = map.get("name");
                models.add(new ModelCode(
                        code.toString().trim(),
                        name == null || name.toString().isBlank() ? null : name.toString().trim()));
            } else if (item instanceof String code && !code.isBlank()) {
                models.add(new ModelCode(code.trim(), null));
            }
        }
        return List.copyOf(models);
    }

    private static Map<String, Object> writeModels(List<ModelCode> models) {
        Map<String, Object> extra = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ModelCode model : models) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", model.code());
            if (model.name() != null && !model.name().isBlank()) {
                row.put("name", model.name());
            }
            rows.add(row);
        }
        extra.put("models", rows);
        return extra;
    }

    private static List<ModelCode> normalizeModels(List<ModelCode> models) {
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ModelCode> normalized = new ArrayList<>();
        for (ModelCode model : models) {
            if (model == null || model.code() == null || model.code().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型代码不能为空");
            }
            String code = model.code().trim();
            if (!seen.add(code)) {
                continue;
            }
            String name = model.name() == null || model.name().isBlank() ? null : model.name().trim();
            normalized.add(new ModelCode(code, name));
        }
        return List.copyOf(normalized);
    }

    private static UpstreamWireApi parseWire(String value) {
        try {
            return UpstreamWireApi.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "…";
    }

    private static String stripSecrets(String body, String apiKey) {
        if (body == null) {
            return null;
        }
        String scrubbed = body;
        if (apiKey != null && !apiKey.isBlank()) {
            scrubbed = scrubbed.replace(apiKey, "***");
        }
        scrubbed = scrubbed.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._\\-]+", "Bearer ***");
        scrubbed = scrubbed.replaceAll("(?i)sk-[A-Za-z0-9]{8,}", "sk-***");
        return scrubbed;
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return truncate(message, 200);
    }

    public record ModelCode(String code, String name) {
    }

    public record UpsertRequest(
            String name,
            String providerType,
            String baseUrl,
            Boolean enabled,
            String apiVersion,
            String upstreamWireApi,
            String translationGateway,
            String apiKey,
            List<ModelCode> models) {
    }

    /** 响应刻意不包含 apiKey / secretId / ciphertext。 */
    public record ProviderView(
            String id,
            String tenantId,
            String name,
            String providerType,
            String baseUrl,
            boolean enabled,
            String apiVersion,
            String upstreamWireApi,
            String translationGateway,
            boolean hasSecret,
            boolean translationGatewayConfigured,
            boolean eligibleForCodex,
            boolean needsTranslationGateway,
            List<ModelCode> models) {
    }

    public record TestResult(boolean ok, int status, long latencyMs, String message, String detail) {
    }
}
