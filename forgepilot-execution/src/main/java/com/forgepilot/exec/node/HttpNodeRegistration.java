package com.forgepilot.exec.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class HttpNodeRegistration implements NodeRegistrationPort {

    private final String controlPlaneBaseUrl;
    private final String internalToken;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public HttpNodeRegistration(
            @Value("${forgepilot.control-plane.base-url:http://127.0.0.1:8080}") String controlPlaneBaseUrl,
            @Value("${forgepilot.internal-api.token:}") String internalToken,
            ObjectMapper objectMapper) {
        this.controlPlaneBaseUrl =
                controlPlaneBaseUrl.endsWith("/")
                        ? controlPlaneBaseUrl.substring(0, controlPlaneBaseUrl.length() - 1)
                        : controlPlaneBaseUrl;
        this.internalToken = internalToken == null ? "" : internalToken;
        this.objectMapper = objectMapper;
    }

    @Override
    public void register(String nodeKey, int capacity) {
        post("/api/v1/internal/nodes/register", Map.of(
                "nodeKey", nodeKey,
                "capacity", capacity,
                "labelsJson", "{\"runtime\":\"execution\",\"docker\":false}"));
    }

    @Override
    public void heartbeat(String nodeKey, int activeSessions) {
        post("/api/v1/internal/nodes/heartbeat", Map.of("nodeKey", nodeKey, "activeSessions", activeSessions));
    }

    private void post(String path, Map<String, Object> body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(controlPlaneBaseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json");
            if (!internalToken.isBlank()) {
                builder.header("X-ForgePilot-Internal-Token", internalToken);
            }
            builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("控制面返回 HTTP " + response.statusCode());
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("调用控制面节点 API 失败: " + ex.getMessage(), ex);
        }
    }
}
