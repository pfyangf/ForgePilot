package com.forgepilot.project.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateReleaseRequest(
        @NotBlank @Size(max = 64) String version,
        @Size(max = 20000) String notes,
        List<String> requirementIds,
        @Valid List<ReleaseTargetRequest> targets) {

    public CreateReleaseRequest(String version, String notes, List<String> requirementIds) {
        this(version, notes, requirementIds, null);
    }

    public record ReleaseTargetRequest(@NotBlank String projectRepositoryId, @NotBlank @Size(max = 256) String targetBranch) {}
}
