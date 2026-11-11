package com.forgepilot.project.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AcceptRequirementRequest(@NotNull Boolean accepted, @Size(max = 20000) String comment) {
}
