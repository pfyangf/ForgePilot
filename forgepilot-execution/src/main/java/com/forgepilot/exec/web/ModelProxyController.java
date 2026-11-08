package com.forgepilot.exec.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.exec.model.InMemoryModelSessionProxy;
import com.forgepilot.exec.model.InMemoryModelSessionProxy.SessionEntry;
import com.forgepilot.exec.model.InMemoryModelSessionProxy.UnauthorizedProxyException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenAI Responses API 兼容表面。Agent 只应调用本接口；Chat Completions 不是 Agent 面向协议。
 */
@RestController
@RequestMapping("/v1")
public class ModelProxyController {

    private final InMemoryModelSessionProxy proxy;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ModelProxyController(InMemoryModelSessionProxy proxy, ObjectMapper objectMapper) {
        this.proxy = proxy;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/responses")
    public ResponseEntity<?> responses(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) Map<String, Object> body) throws Exception {
        SessionEntry session = proxy.requireValidToken(authorization);
        if (session.upstreamBaseUrl() == null
                || session.upstreamBaseUrl().isBlank()
                || session.upstreamApiKey() == null
                || session.upstreamApiKey().isBlank()
                || session.upstreamApiKey().startsWith("PLACEHOLDER")) {
            Map<String, Object> stub = new LinkedHashMap<>();
            stub.put("id", "resp_local_" + session.sessionId());
            stub.put("object", "response");
            stub.put("status", "completed");
            stub.put("model", session.modelId());
            stub.put("wire_api", InMemoryModelSessionProxy.WIRE_API);
            stub.put(
                    "output",
                    List.of(Map.of(
                            "type",
                            "message",
                            "role",
                            "assistant",
                            "content",
                            List.of(Map.of(
                                    "type",
                                    "output_text",
                                    "text",
                                    "ForgePilot local model proxy accepted Responses request for session "
                                            + session.sessionId()
                                            + ". Upstream provider key is not configured for live calls.")))));
            return ResponseEntity.ok(stub);
        }
        String base = session.upstreamBaseUrl().replaceAll("/$", "");
        if (!base.endsWith("/v1")) {
            base = base + "/v1";
        }
        String target = base + "/responses";
        HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + session.upstreamApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body == null ? Map.of() : body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return ResponseEntity.status(response.statusCode()).body(response.body());
    }

    @PostMapping("/chat/completions")
    public ResponseEntity<?> rejectChatCompletions() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of(
                        "error",
                        "chat_completions_not_supported",
                        "message",
                        "Agent-facing protocol is OpenAI Responses only (wire_api=responses)"));
    }

    @ExceptionHandler(UnauthorizedProxyException.class)
    public ResponseEntity<?> unauthorized(UnauthorizedProxyException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "unauthorized", "message", ex.getMessage()));
    }
}
