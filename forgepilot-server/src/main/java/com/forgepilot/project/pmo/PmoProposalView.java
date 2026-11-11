package com.forgepilot.project.pmo;

import com.forgepilot.execution.service.TaskService.TaskView;
import java.util.List;

public record PmoProposalView(
        String proposalId,
        String kind,
        String taskType,
        String defectId,
        String targetTaskId,
        String title,
        String hint,
        String primaryActionLabel,
        boolean confirmCreatesTask,
        String requirementId,
        String requirementCode,
        String requirementTitle,
        String projectId,
        String projectName) {

    public record ListView(List<PmoProposalView> proposals) {}

    public record ConfirmResult(PmoProposalView proposal, TaskView task) {}
}
