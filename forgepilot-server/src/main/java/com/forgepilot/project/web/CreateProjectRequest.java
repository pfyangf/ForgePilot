package com.forgepilot.project.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank
                @Size(max = 64)
                @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$", message = "项目编码只能包含字母数字下划线和短横线")
                String code,
        @Size(max = 4000) String description,
        @Size(max = 128) String techStack) {
}
