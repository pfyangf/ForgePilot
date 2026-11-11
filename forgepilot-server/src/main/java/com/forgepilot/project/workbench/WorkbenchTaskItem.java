package com.forgepilot.project.workbench;

public record WorkbenchTaskItem(
        String id,
        String code,
        String requirementId,
        String requirementCode,
        String title,
        String taskType,
        String status,
        String sessionStatus,
        String createdBy,
        String ownerId,
        String projectId,
        String projectName,
        String branchName,
        String agentLabel,
        boolean interactiveInput,
        boolean interrupted) {
}
