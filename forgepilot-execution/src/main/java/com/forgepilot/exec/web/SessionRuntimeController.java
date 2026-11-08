package com.forgepilot.exec.web;

import com.forgepilot.exec.agent.AgentNotAvailableException;
import com.forgepilot.exec.session.SessionRuntimeService;
import com.forgepilot.exec.session.SessionRuntimeService.StartRequest;
import com.forgepilot.exec.session.SessionRuntimeService.StartResult;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 控制面调用的内部运行时 API。上游 Provider Key 只在此请求体中短暂出现，不会返回给前端或写入 Agent 配置。
 */
@RestController
@RequestMapping("/api/v1/runtime/sessions")
public class SessionRuntimeController {

    private final SessionRuntimeService runtimeService;

    public SessionRuntimeController(SessionRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @PostMapping("/start")
    public StartResult start(@RequestBody StartRequest request) {
        return runtimeService.start(request);
    }

    @PostMapping("/{sessionId}/cancel")
    public Map<String, String> cancel(@PathVariable String sessionId) {
        runtimeService.cancel(sessionId);
        return Map.of("sessionId", sessionId, "status", "CANCELLED");
    }

    @PostMapping("/{sessionId}/input")
    public Map<String, String> input(@PathVariable String sessionId, @RequestBody Map<String, String> body) {
        runtimeService.sendInput(sessionId, body.getOrDefault("message", ""));
        return Map.of("sessionId", sessionId, "status", "ACCEPTED");
    }

    @PostMapping("/{sessionId}/revoke-token")
    public Map<String, String> revoke(@PathVariable String sessionId) {
        runtimeService.revokeToken(sessionId);
        return Map.of("sessionId", sessionId, "revoked", "true");
    }

    @GetMapping("/{sessionId}/logs")
    public Map<String, Object> logs(@PathVariable String sessionId, @RequestParam(defaultValue = "200") int limit) {
        List<String> lines = runtimeService.tailLogs(sessionId, limit);
        return Map.of("sessionId", sessionId, "lines", lines);
    }

    @ExceptionHandler(AgentNotAvailableException.class)
    public ResponseEntity<?> notAvailable(AgentNotAvailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", AgentNotAvailableException.CODE, "message", ex.getMessage()));
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<?> unsupported(UnsupportedOperationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "UNSUPPORTED", "message", ex.getMessage()));
    }
}
