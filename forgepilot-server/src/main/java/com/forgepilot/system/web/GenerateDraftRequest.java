package com.forgepilot.system.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateDraftRequest(
        @NotBlank @Size(max = 64) String scene,
        String projectId,
        String requirementId,
        String taskId,
        String defectId,
        @Size(max = 8000) String hint,
        String modelProviderId,
        String modelId) {}
