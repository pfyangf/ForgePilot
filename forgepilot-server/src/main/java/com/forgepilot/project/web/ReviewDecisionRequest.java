package com.forgepilot.project.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewDecisionRequest(
        @NotBlank
                @Pattern(regexp = "APPROVED|REJECTED", message = "评审结论必须是 APPROVED 或 REJECTED")
                String decision,
        @Size(max = 20000) String comment) {
}
