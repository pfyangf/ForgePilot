package com.forgepilot.project.pmo;

/** 规划器产出的下一步建议。只有 CREATE_TASK 在确认时会创建任务。 */
public record PmoCandidate(
        String kind,
        String taskType,
        String defectId,
        String targetTaskId,
        String title,
        String hint,
        String primaryActionLabel,
        String fingerprint,
        String proposalId,
        boolean confirmCreatesTask) {}
