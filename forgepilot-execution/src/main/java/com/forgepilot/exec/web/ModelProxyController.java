package com.forgepilot.exec.web;

import com.forgepilot.exec.model.InMemoryModelSessionProxy;
import com.forgepilot.exec.model.InMemoryModelSessionProxy.SessionEntry;
import com.forgepilot.exec.model.InMemoryModelSessionProxy.UnauthorizedProxyException;
import com.forgepilot.exec.model.UpstreamResponsesClient;
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

@RestController
@RequestMapping("/v1")
public class ModelProxyController {

    private final InMemoryModelSessionProxy proxy;
    private final UpstreamResponsesClient upstreamResponsesClient;

    public ModelProxyController(InMemoryModelSessionProxy proxy, UpstreamResponsesClient upstreamResponsesClient) {
        this.proxy = proxy;
        this.upstreamResponsesClient = upstreamResponsesClient;
    }

    @PostMapping("/responses")
    public ResponseEntity<?> responses(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) Map<String, Object> body) {
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
            stub.put("mode", "stub");
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
                                    "ForgePilot local model proxy stub for session "
                                            + session.sessionId()
                                            + ". Upstream key missing or PLACEHOLDER_*.")))));
            // never echo api key
            return ResponseEntity.ok(stub);
        }
        var upstream = upstreamResponsesClient.postResponses(
                session.upstreamBaseUrl(), session.upstreamApiKey(), body);
        String responseBody = upstream.body() == null ? "" : upstream.body();
        if (session.upstreamApiKey() != null && responseBody.contains(session.upstreamApiKey())) {
            responseBody = responseBody.replace(session.upstreamApiKey(), "***");
        }
        return ResponseEntity.status(upstream.statusCode()).body(responseBody);
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
