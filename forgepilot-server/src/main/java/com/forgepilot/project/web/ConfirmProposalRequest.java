package com.forgepilot.project.web;

import jakarta.validation.constraints.Size;

public record ConfirmProposalRequest(
        @Size(max = 256) String title, @Size(max = 20000) String comment) {}
