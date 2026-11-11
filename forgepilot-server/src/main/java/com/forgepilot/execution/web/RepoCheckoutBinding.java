package com.forgepilot.execution.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RepoCheckoutBinding(
        @NotBlank String projectRepositoryId,
        @Size(max = 256) String sourceBranch,
        @Size(max = 256) String workingBranch) {}
