package com.forgepilot.project.pmo;

import com.forgepilot.project.lifecycle.RequirementStatusMachine.GateSnapshot;
import java.util.List;

/** 规划器输入：需求当前状态、门禁、任务和缺陷的只读快照。 */
public record PmoSnapshot(
        String requirementId,
        String requirementCode,
        String requirementTitle,
        String status,
        String projectId,
        GateSnapshot gates,
        List<TaskInfo> tasks,
        List<DefectInfo> defects) {

    public record TaskInfo(String id, String taskType, String status, String defectId) {}

    public record DefectInfo(String id, String code, String title, String status) {}
}
