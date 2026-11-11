package com.forgepilot.project.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveDocumentRequest(
        @NotBlank @Size(max = 256) String title, @Size(max = 200000) String body) {
}
