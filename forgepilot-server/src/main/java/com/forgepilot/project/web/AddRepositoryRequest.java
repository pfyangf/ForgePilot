package com.forgepilot.project.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddRepositoryRequest(
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(max = 512) String cloneUrl,
        @NotBlank @Size(max = 128) String localDirectoryName,
        @Size(max = 256) String defaultBranch,
        @Size(max = 32) String providerType) {
}
