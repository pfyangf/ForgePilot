package com.forgepilot.project.web;

import jakarta.validation.constraints.Size;

public record ReturnProposalRequest(@Size(max = 20000) String comment) {}
