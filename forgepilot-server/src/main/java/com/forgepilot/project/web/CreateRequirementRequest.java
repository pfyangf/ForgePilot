package com.forgepilot.project.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRequirementRequest(
        @NotBlank @Size(max = 256) String title,
        @Size(max = 20000) String description,
        @Size(max = 20000) String background,
        @Size(max = 20000) String scope,
        @Size(max = 20000) String acceptance,
        @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "优先级必须是 HIGH、MEDIUM 或 LOW") String priority,
        String ownerId) {
}
