package com.forgepilot.project.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateReleaseRequest(
        @NotBlank @Size(max = 64) String version,
        @Size(max = 20000) String notes,
        List<String> requirementIds) {
}
