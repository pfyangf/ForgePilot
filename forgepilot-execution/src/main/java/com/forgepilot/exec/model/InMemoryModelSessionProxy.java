package com.forgepilot.exec.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 会话级模型代理凭证发放。对 Agent 只给 baseUrl + 短时令牌；长期 Provider Key 仅留在本组件内存，
 * 供 Responses 转发使用，绝不写入 Agent 配置或日志。
 */
@Component
@Primary
public class InMemoryModelSessionProxy implements ModelSessionProxy {

    public static final String WIRE_API = "responses";

    private final String bindHost;
    private final int bindPort;
    private final long ttlSeconds;
    private final ConcurrentHashMap<String, SessionEntry> bySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SessionEntry> byToken = new ConcurrentHashMap<>();

    public InMemoryModelSessionProxy(
            @Value("${forgepilot.model-proxy.bind:127.0.0.1}") String bindHost,
            @Value("${forgepilot.model-proxy.port:8082}") int bindPort,
            @Value("${forgepilot.model-proxy.token-ttl-seconds:3600}") long ttlSeconds) {
        this.bindHost = bindHost;
        this.bindPort = bindPort;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public String exposedWireApi() {
        return WIRE_API;
    }

    @Override
    public ModelSessionCredential issue(ModelSessionRequest request) {
        revoke(request.sessionId());
        String token = "fp_ms_" + UUID.randomUUID().toString().replace("-", "");
        Instant expires = Instant.now().plusSeconds(ttlSeconds);
        SessionEntry entry = new SessionEntry(
                request.sessionId(),
                request.providerId(),
                request.modelId(),
                request.upstreamBaseUrl(),
                request.upstreamApiKey(),
                token,
                expires,
                request.upstreamWireApi() == null ? WIRE_API : request.upstreamWireApi());
        bySession.put(request.sessionId(), entry);
        byToken.put(token, entry);
        String baseUrl = "http://" + bindHost + ":" + bindPort + "/v1";
        return new ModelSessionCredential(request.sessionId(), request.providerId(), baseUrl, token);
    }

    @Override
    public void revoke(String sessionId) {
        SessionEntry existing = bySession.remove(sessionId);
        if (existing != null) {
            byToken.remove(existing.token());
        }
    }

    public SessionEntry requireValidToken(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new UnauthorizedProxyException("缺少会话令牌");
        }
        String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7).trim() : bearerToken.trim();
        SessionEntry entry = byToken.get(token);
        if (entry == null) {
            throw new UnauthorizedProxyException("会话令牌无效或已撤销");
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            byToken.remove(token);
            bySession.remove(entry.sessionId());
            throw new UnauthorizedProxyException("会话令牌已过期");
        }
        return entry;
    }

    public Map<String, Object> agentFacingConfig(ModelSessionCredential credential) {
        return Map.of(
                "wire_api", WIRE_API,
                "base_url", credential.baseUrl(),
                "session_token", credential.shortLivedToken(),
                // 刻意不包含 apiKey / upstreamApiKey / provider secret
                "note", "Agent must call Responses via session proxy only");
    }

    public record SessionEntry(
            String sessionId,
            String providerId,
            String modelId,
            String upstreamBaseUrl,
            String upstreamApiKey,
            String token,
            Instant expiresAt,
            String upstreamWireApi) {
    }

    public static class UnauthorizedProxyException extends RuntimeException {
        public UnauthorizedProxyException(String message) {
            super(message);
        }
    }
}
