package com.forgepilot.project.workbench;

public record WorkbenchProposalItem(
        String proposalId,
        String kind,
        String taskType,
        String defectId,
        String title,
        String hint,
        String primaryActionLabel,
        boolean confirmCreatesTask,
        String requirementId,
        String requirementCode,
        String requirementTitle,
        String projectId,
        String projectName) {}
