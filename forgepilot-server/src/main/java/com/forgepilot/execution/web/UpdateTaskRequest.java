package com.forgepilot.execution.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateTaskRequest(
        @Size(max = 256) String title,
        @Size(max = 20000) String description,
        @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "优先级必须是 HIGH、MEDIUM 或 LOW") String priority,
        String predecessorTaskId,
        @Size(max = 256) String workingBranch,
        @Valid List<RepoCheckoutBinding> repositories,
        String modelProviderId,
        String modelId,
        String agentProfileId) {}
