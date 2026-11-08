package com.forgepilot.project.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ConfirmAnalysisRequest(
        @NotBlank @Size(max = 50000) String draft,
        List<@Size(max = 256) String> taskTitles) {
}
