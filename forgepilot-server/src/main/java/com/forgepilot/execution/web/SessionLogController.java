package com.forgepilot.execution.web;

import com.forgepilot.execution.service.TaskService;
import com.forgepilot.execution.stream.SessionLogStream;
import com.forgepilot.execution.stream.SessionLogStream.LogChunk;
import com.forgepilot.system.security.AuthContext;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class SessionLogController {

    private final SessionLogStream sessionLogStream;
    private final TaskService taskService;

    public SessionLogController(SessionLogStream sessionLogStream, TaskService taskService) {
        this.sessionLogStream = sessionLogStream;
        this.taskService = taskService;
    }

    @GetMapping("/tasks/{taskId}/sessions/{sessionId}/logs")
    public Map<String, Object> poll(
            @PathVariable String taskId,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "0") long afterSeq,
            @RequestParam(defaultValue = "200") int limit) {
        AuthContext.require();
        // authorize via task membership
        taskService.get(AuthContext.require(), taskId);
        List<LogChunk> chunks = sessionLogStream.readAfter(sessionId, afterSeq, limit);
        return Map.of("sessionId", sessionId, "chunks", chunks);
    }

    @GetMapping(path = "/tasks/{taskId}/sessions/{sessionId}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String taskId,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "0") long afterSeq) {
        AuthContext.require();
        taskService.get(AuthContext.require(), taskId);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicLong cursor = new AtomicLong(afterSeq);
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(
                () -> {
                    try {
                        List<LogChunk> chunks = sessionLogStream.readAfter(sessionId, cursor.get(), 50);
                        for (LogChunk chunk : chunks) {
                            emitter.send(SseEmitter.event().id(String.valueOf(chunk.seq())).data(chunk.body()));
                            cursor.set(chunk.seq());
                        }
                    } catch (IOException | IllegalStateException ex) {
                        emitter.completeWithError(ex);
                        exec.shutdown();
                    }
                },
                0,
                1,
                TimeUnit.SECONDS);
        emitter.onCompletion(exec::shutdown);
        emitter.onTimeout(exec::shutdown);
        return emitter;
    }

    /** 执行侧推送日志块（本机未配内部令牌时放行）。 */
    @PostMapping("/internal/sessions/{sessionId}/logs")
    public Map<String, Object> append(@PathVariable String sessionId, @RequestBody Map<String, String> body) {
        long seq = sessionLogStream.append(sessionId, body.getOrDefault("chunk", ""));
        return Map.of("sessionId", sessionId, "seq", seq);
    }
}
