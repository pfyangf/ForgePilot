package com.forgepilot.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.domain.IntegrationSecret;
import com.forgepilot.system.domain.ModelProvider;
import com.forgepilot.system.model.CodexProviderEligibility;
import com.forgepilot.system.model.UpstreamWireApi;
import com.forgepilot.system.persistence.IntegrationSecretMapper;
import com.forgepilot.system.persistence.ModelProviderMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModelProviderCatalogService {

    private final ModelProviderMapper modelProviderMapper;
    private final IntegrationSecretMapper integrationSecretMapper;
    private final RoleAccess roleAccess;
    private final ActivityLogRecorder activityLogRecorder;

    public ModelProviderCatalogService(
            ModelProviderMapper modelProviderMapper,
            IntegrationSecretMapper integrationSecretMapper,
            RoleAccess roleAccess,
            ActivityLogRecorder activityLogRecorder) {
        this.modelProviderMapper = modelProviderMapper;
        this.integrationSecretMapper = integrationSecretMapper;
        this.roleAccess = roleAccess;
        this.activityLogRecorder = activityLogRecorder;
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
                        "secretRotated", request.apiKey() != null && !request.apiKey().isBlank()),
                "SUCCESS");
        return toView(provider);
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
        provider.setProviderType(request.providerType().trim().toUpperCase());
        provider.setBaseUrl(request.baseUrl().trim());
        provider.setEnabled(request.enabled() == null || request.enabled());
        provider.setApiVersion(blankToNull(request.apiVersion()));
        provider.setUpstreamWireApi(switch (wire) {
            case RESPONSES -> "responses";
            case CHAT_COMPLETIONS -> "chat_completions";
            case NATIVE -> "native";
        });
        provider.setTranslationGateway(blankToNull(request.translationGateway()));
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
                CodexProviderEligibility.needsTranslationGateway(wire, provider.getTranslationGateway()));
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

    public record UpsertRequest(
            String name,
            String providerType,
            String baseUrl,
            Boolean enabled,
            String apiVersion,
            String upstreamWireApi,
            String translationGateway,
            String apiKey) {
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
            boolean needsTranslationGateway) {
    }
}
