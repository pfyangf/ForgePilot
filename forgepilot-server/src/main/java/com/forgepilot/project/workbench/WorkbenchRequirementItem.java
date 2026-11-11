package com.forgepilot.project.workbench;

public record WorkbenchRequirementItem(
        String id,
        String code,
        String title,
        String status,
        String projectId,
        String projectName,
        String createdBy,
        String ownerId,
        String draft,
        boolean confirmed,
        int taskDone,
        int taskTotal) {
}
