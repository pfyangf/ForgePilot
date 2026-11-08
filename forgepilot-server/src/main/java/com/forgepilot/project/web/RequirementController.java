package com.forgepilot.project.web;

import com.forgepilot.execution.service.TaskService;
import com.forgepilot.execution.service.TaskService.TaskView;
import com.forgepilot.execution.web.CreateTaskRequest;
import com.forgepilot.project.service.RequirementService;
import com.forgepilot.project.service.RequirementService.RequirementDetailView;
import com.forgepilot.system.security.AuthContext;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/requirements")
public class RequirementController {

    private final RequirementService requirementService;
    private final TaskService taskService;

    public RequirementController(RequirementService requirementService, TaskService taskService) {
        this.requirementService = requirementService;
        this.taskService = taskService;
    }

    @GetMapping("/{requirementId}")
    public RequirementDetailView get(@PathVariable String requirementId) {
        return requirementService.get(AuthContext.require(), requirementId);
    }

    @PostMapping("/{requirementId}/confirm-analysis")
    public RequirementDetailView confirmAnalysis(
            @PathVariable String requirementId, @Valid @RequestBody ConfirmAnalysisRequest request) {
        return requirementService.confirmAnalysis(AuthContext.require(), requirementId, request);
    }

    @GetMapping("/{requirementId}/tasks")
    public List<TaskView> tasks(@PathVariable String requirementId) {
        return taskService.listByRequirement(AuthContext.require(), requirementId);
    }

    @PostMapping("/{requirementId}/tasks")
    public TaskView createTask(@PathVariable String requirementId, @Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(AuthContext.require(), requirementId, request);
    }
}
