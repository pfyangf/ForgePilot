package com.forgepilot.execution.web;

import jakarta.validation.constraints.Size;

public record TaskActionRequest(@Size(max = 4000) String note) {
}
