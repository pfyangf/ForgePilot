package com.forgepilot.execution.web;

import com.forgepilot.execution.service.TaskService;
import com.forgepilot.execution.service.TaskService.TaskDetailView;
import com.forgepilot.system.security.AuthContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{taskId}")
    public TaskDetailView get(@PathVariable String taskId) {
        return taskService.get(AuthContext.require(), taskId);
    }

    @PostMapping("/{taskId}/cancel")
    public TaskDetailView cancel(@PathVariable String taskId, @Valid @RequestBody(required = false) TaskActionRequest request) {
        return taskService.cancel(AuthContext.require(), taskId, request == null ? new TaskActionRequest(null) : request);
    }

    @PostMapping("/{taskId}/abandon")
    public TaskDetailView abandon(@PathVariable String taskId, @Valid @RequestBody(required = false) TaskActionRequest request) {
        return taskService.abandon(AuthContext.require(), taskId, request == null ? new TaskActionRequest(null) : request);
    }

    @PostMapping("/{taskId}/approve")
    public TaskDetailView approve(@PathVariable String taskId, @Valid @RequestBody(required = false) TaskActionRequest request) {
        return taskService.approve(AuthContext.require(), taskId, request == null ? new TaskActionRequest(null) : request);
    }
}
