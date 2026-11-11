package com.forgepilot.execution.web;

import com.forgepilot.delivery.service.TaskVerifyDeliveryService;
import com.forgepilot.delivery.service.TaskVerifyDeliveryService.VerifyDeliveryResult;
import com.forgepilot.execution.service.TaskDocumentPackService;
import com.forgepilot.execution.service.TaskDocumentPackService.DocumentPackView;
import com.forgepilot.execution.service.TaskService;
import com.forgepilot.execution.service.TaskService.TaskDetailView;
import com.forgepilot.execution.service.TaskSessionService;
import com.forgepilot.execution.service.TaskSessionService.SessionActionResult;
import com.forgepilot.execution.service.TaskSessionService.SessionView;
import com.forgepilot.system.security.AuthContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskVerifyDeliveryService verifyDeliveryService;
    private final TaskSessionService taskSessionService;
    private final TaskDocumentPackService documentPackService;

    public TaskController(
            TaskService taskService,
            TaskVerifyDeliveryService verifyDeliveryService,
            TaskSessionService taskSessionService,
            TaskDocumentPackService documentPackService) {
        this.taskService = taskService;
        this.verifyDeliveryService = verifyDeliveryService;
        this.taskSessionService = taskSessionService;
        this.documentPackService = documentPackService;
    }

    @GetMapping("/{taskId}")
    public TaskDetailView get(@PathVariable String taskId) {
        return taskService.get(AuthContext.require(), taskId);
    }

    @PatchMapping("/{taskId}")
    public TaskDetailView update(@PathVariable String taskId, @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(AuthContext.require(), taskId, request);
    }

    @GetMapping("/{taskId}/document-pack")
    public DocumentPackView documentPack(
            @PathVariable String taskId, @RequestParam(required = false) String sessionId) {
        return documentPackService.frozenOrPreview(AuthContext.require(), taskId, sessionId);
    }

    @PostMapping("/{taskId}/start")
    public SessionActionResult start(
            @PathVariable String taskId, @Valid @RequestBody(required = false) TaskActionRequest request) {
        return taskSessionService.start(
                AuthContext.require(), taskId, request == null ? new TaskActionRequest(null) : request);
    }

    @PostMapping("/{taskId}/retry")
    public SessionActionResult retry(
            @PathVariable String taskId, @Valid @RequestBody(required = false) TaskActionRequest request) {
        return taskSessionService.retry(
                AuthContext.require(), taskId, request == null ? new TaskActionRequest(null) : request);
    }

    @PostMapping("/{taskId}/input")
    public SessionActionResult input(
            @PathVariable String taskId, @Valid @RequestBody(required = false) TaskActionRequest request) {
        return taskSessionService.sendInput(
                AuthContext.require(), taskId, request == null ? new TaskActionRequest(null) : request);
    }

    @PostMapping("/{taskId}/request-changes")
    public SessionActionResult requestChanges(
            @PathVariable String taskId, @Valid @RequestBody(required = false) TaskActionRequest request) {
        return taskSessionService.requestChanges(
                AuthContext.require(), taskId, request == null ? new TaskActionRequest(null) : request);
    }

    @GetMapping("/{taskId}/session")
    public SessionView session(@PathVariable String taskId) {
        SessionView view = taskSessionService.getActiveSession(AuthContext.require(), taskId);
        if (view == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "尚无 Session");
        }
        return view;
    }

    @PostMapping("/{taskId}/cancel")
    public TaskDetailView cancel(@PathVariable String taskId, @Valid @RequestBody(required = false) TaskActionRequest request) {
        TaskActionRequest body = request == null ? new TaskActionRequest(null) : request;
        try {
            taskSessionService.cancelSession(AuthContext.require(), taskId, body);
        } catch (Exception ignored) {
            // no active session is fine
        }
        try {
            return taskService.cancel(AuthContext.require(), taskId, body);
        } catch (Exception ex) {
            // Session cancel may have already moved task to CANCELLED
            return taskService.get(AuthContext.require(), taskId);
        }
    }

    @PostMapping("/{taskId}/abandon")
    public TaskDetailView abandon(@PathVariable String taskId, @Valid @RequestBody(required = false) TaskActionRequest request) {
        return taskService.abandon(AuthContext.require(), taskId, request == null ? new TaskActionRequest(null) : request);
    }

    @PostMapping("/{taskId}/approve")
    public TaskDetailView approve(@PathVariable String taskId, @Valid @RequestBody(required = false) TaskActionRequest request) {
        return taskService.approve(AuthContext.require(), taskId, request == null ? new TaskActionRequest(null) : request);
    }

    /** 本机/开发验证：不假装 Codex 已跑；基于冻结仓库工作区执行项目命令并尝试交付。 */
    @PostMapping("/{taskId}/verify")
    public VerifyDeliveryResult verify(@PathVariable String taskId) {
        return verifyDeliveryService.verify(AuthContext.require(), taskId);
    }

    @PostMapping("/{taskId}/retry-delivery")
    public VerifyDeliveryResult retryDelivery(@PathVariable String taskId) {
        return verifyDeliveryService.retryDelivery(AuthContext.require(), taskId);
    }
}
