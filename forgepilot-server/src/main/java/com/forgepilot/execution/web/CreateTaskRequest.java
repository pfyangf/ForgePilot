package com.forgepilot.execution.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateTaskRequest(
        @NotBlank @Size(max = 256) String title,
        @Size(max = 20000) String description,
        @NotBlank
                @Pattern(
                        regexp = "IMPLEMENT|BUGFIX|REFACTOR|TEST|DOCS|REVIEW",
                        message = "任务类型不在允许范围")
                String taskType,
        @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "优先级必须是 HIGH、MEDIUM 或 LOW") String priority,
        String ownerId,
        String predecessorTaskId,
        String defectId,
        @Size(max = 256) String workingBranch,
        @Valid List<RepoCheckoutBinding> repositories,
        String modelProviderId,
        String modelId,
        String agentProfileId) {

    public CreateTaskRequest(
            String title,
            String description,
            String taskType,
            String priority,
            String ownerId,
            String predecessorTaskId) {
        this(title, description, taskType, priority, ownerId, predecessorTaskId, null, null, null, null, null, null);
    }

    public CreateTaskRequest(
            String title,
            String description,
            String taskType,
            String priority,
            String ownerId,
            String predecessorTaskId,
            String defectId) {
        this(title, description, taskType, priority, ownerId, predecessorTaskId, defectId, null, null, null, null, null);
    }
}
