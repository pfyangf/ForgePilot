package com.forgepilot.execution.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpAgentRuntimeClient implements AgentRuntimePort {

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public HttpAgentRuntimeClient(
            @Value("${forgepilot.execution.base-url:http://127.0.0.1:19081}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
    }

    @Override
    public StartResult start(StartCommand command) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("taskId", command.taskId());
            body.put("sessionId", command.sessionId());
            body.put("workspacePath", command.workspacePath());
            body.put("agentHomePath", command.agentHomePath());
            body.put("providerId", command.providerId());
            body.put("modelId", command.modelId());
            body.put("upstreamBaseUrl", command.upstreamBaseUrl());
            body.put("upstreamApiKey", command.upstreamApiKey());
            body.put("upstreamWireApi", command.upstreamWireApi());
            body.put("prompt", command.prompt());
            HttpResponse<String> response = exchange("POST", "/api/v1/runtime/sessions/start", body);
            if (response.statusCode() == 503) {
                JsonNode err = objectMapper.readTree(response.body());
                return new StartResult(
                        command.sessionId(),
                        "FAILED",
                        null,
                        null,
                        Map.of(),
                        false,
                        text(err, "error", "NOT_AVAILABLE"),
                        text(err, "message", "执行节点不可用"));
            }
            if (response.statusCode() >= 400) {
                return new StartResult(
                        command.sessionId(),
                        "FAILED",
                        null,
                        null,
                        Map.of(),
                        false,
                        "RUNTIME_ERROR",
                        "执行节点返回 HTTP " + response.statusCode());
            }
            JsonNode json = objectMapper.readTree(response.body());
            boolean interactive = json.path("capabilities").path("interactiveInput").asBoolean(false);
            Map<String, Object> agentConfig = objectMapper.convertValue(
                    json.path("agentFacingConfig"),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            // Never return upstream key
            return new StartResult(
                    command.sessionId(),
                    text(json, "runtimeStatus", "RUNNING"),
                    text(json, "modelProxyBaseUrl", null),
                    text(json, "wireApi", "responses"),
                    agentConfig == null ? Map.of() : agentConfig,
                    interactive,
                    null,
                    null);
        } catch (Exception ex) {
            return new StartResult(
                    command.sessionId(),
                    "FAILED",
                    null,
                    null,
                    Map.of(),
                    false,
                    "NOT_AVAILABLE",
                    "无法连接执行服务: " + ex.getMessage());
        }
    }

    @Override
    public void cancel(String sessionId) {
        try {
            exchange("POST", "/api/v1/runtime/sessions/" + sessionId + "/cancel", Map.of());
        } catch (Exception ignored) {
            // best-effort
        }
    }

    @Override
    public void sendInput(String sessionId, String message) {
        try {
            HttpResponse<String> response =
                    exchange("POST", "/api/v1/runtime/sessions/" + sessionId + "/input", Map.of("message", message));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("发送输入失败: HTTP " + response.statusCode());
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("发送输入失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void revokeModelToken(String sessionId) {
        try {
            exchange("POST", "/api/v1/runtime/sessions/" + sessionId + "/revoke-token", Map.of());
        } catch (Exception ignored) {
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> tailLogs(String sessionId, int limit) {
        try {
            HttpResponse<String> response =
                    exchange("GET", "/api/v1/runtime/sessions/" + sessionId + "/logs?limit=" + limit, null);
            if (response.statusCode() >= 400) {
                return List.of();
            }
            JsonNode json = objectMapper.readTree(response.body());
            List<String> lines = new ArrayList<>();
            if (json.path("lines").isArray()) {
                json.path("lines").forEach(n -> lines.add(n.asText()));
            }
            return lines;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private HttpResponse<String> exchange(String method, String path, Object body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30));
        if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(
                    method,
                    HttpRequest.BodyPublishers.ofString(body == null ? "{}" : objectMapper.writeValueAsString(body)));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return defaultValue;
        }
        return value.asText();
    }
}
