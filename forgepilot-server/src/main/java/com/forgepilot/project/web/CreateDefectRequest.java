package com.forgepilot.project.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDefectRequest(
        @NotBlank @Size(max = 256) String title,
        @Size(max = 20000) String description,
        @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "严重程度必须是 HIGH、MEDIUM 或 LOW") String severity,
        String taskId) {
}
