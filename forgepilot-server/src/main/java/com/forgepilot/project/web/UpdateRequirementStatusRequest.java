package com.forgepilot.project.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRequirementStatusRequest(
        @NotBlank @Size(max = 32) String status, @Size(max = 20000) String note) {
}
