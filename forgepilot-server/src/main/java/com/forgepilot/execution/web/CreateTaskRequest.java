package com.forgepilot.execution.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
        String predecessorTaskId) {
}
