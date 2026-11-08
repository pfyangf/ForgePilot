package com.forgepilot.project.workbench;

import java.util.List;

public record WorkbenchSnapshot(
        String scope,
        List<WorkbenchRequirementItem> requirements,
        List<WorkbenchTaskItem> tasks,
        WorkbenchCounts counts) {

    public record WorkbenchCounts(
            int requirements, int tasks, int running, int verifying, int approval, int waitingUser) {
    }
}
