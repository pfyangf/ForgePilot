package com.forgepilot.project.web;

import jakarta.validation.constraints.Size;

public record DefectActionRequest(@Size(max = 20000) String note) {
}
