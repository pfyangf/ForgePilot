package com.forgepilot.project.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgepilot.project.lifecycle.RequirementStatusMachine.DefectSnapshot;
import com.forgepilot.project.lifecycle.RequirementStatusMachine.GateSnapshot;
import com.forgepilot.project.lifecycle.RequirementStatusMachine.TaskSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequirementStatusMachineTest {

    private static final GateSnapshot OPEN_GATES = new GateSnapshot(false, false, false, false, false, false);
    private static final GateSnapshot READY_GATES = new GateSnapshot(true, true, true, true, true, true);

    @Test
    void confirmAnalysisGoesToRequirementReviewNotDevelopment() {
        assertThat(RequirementStatusMachine.afterConfirmAnalysis()).isEqualTo("待需求评审");
    }

    @Test
    void requirementReviewTransitions() {
        assertThat(RequirementStatusMachine.afterRequirementReview("APPROVED")).isEqualTo("设计中");
        assertThat(RequirementStatusMachine.afterRequirementReview("REJECTED")).isEqualTo("待分析");
        assertThatThrownBy(() -> RequirementStatusMachine.afterRequirementReview("PENDING"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void designReviewTransitions() {
        assertThat(RequirementStatusMachine.afterSubmitDesignReview()).isEqualTo("待设计评审");
        assertThat(RequirementStatusMachine.afterDesignReview("APPROVED")).isEqualTo("待开发");
        assertThat(RequirementStatusMachine.afterDesignReview("REJECTED")).isEqualTo("设计中");
    }

    @Test
    void acceptanceAndReleasePreconditions() {
        assertThat(RequirementStatusMachine.canAccept("待验收", false)).isTrue();
        assertThat(RequirementStatusMachine.canAccept("待验收", true)).isFalse();
        assertThat(RequirementStatusMachine.canAccept("测试中", false)).isFalse();
        assertThat(RequirementStatusMachine.afterAcceptance(true, false)).isEqualTo("已完成");
        assertThat(RequirementStatusMachine.afterAcceptance(false, true)).isEqualTo("开发中");
        assertThat(RequirementStatusMachine.afterAcceptance(false, false)).isEqualTo("测试中");
        assertThat(RequirementStatusMachine.canRelease("已完成")).isTrue();
        assertThat(RequirementStatusMachine.canRelease("待验收")).isFalse();
        assertThat(RequirementStatusMachine.canManuallySet("测试中")).isTrue();
        assertThat(RequirementStatusMachine.canManuallySet("已发布")).isFalse();
    }

    @Test
    void autoStatusFollowsTasksAndBlockingDefects() {
        assertThat(RequirementStatusMachine.autoStatus(
                        "待开发",
                        List.of(new TaskSnapshot("IMPLEMENT", "RUNNING")),
                        false))
                .isEqualTo("开发中");
        assertThat(RequirementStatusMachine.autoStatus(
                        "开发中",
                        List.of(new TaskSnapshot("TEST", "VERIFYING")),
                        false))
                .isEqualTo("测试中");
        assertThat(RequirementStatusMachine.autoStatus(
                        "测试中",
                        List.of(new TaskSnapshot("IMPLEMENT", "COMPLETED"), new TaskSnapshot("TEST", "COMPLETED")),
                        false))
                .isEqualTo("待验收");
        assertThat(RequirementStatusMachine.autoStatus(
                        "测试中",
                        List.of(new TaskSnapshot("IMPLEMENT", "COMPLETED")),
                        true))
                .isEqualTo("开发中");
        assertThat(RequirementStatusMachine.autoStatus(
                        "已完成",
                        List.of(new TaskSnapshot("IMPLEMENT", "RUNNING")),
                        false))
                .isEqualTo("已完成");
        assertThat(RequirementStatusMachine.autoStatus(
                        "已发布", List.of(new TaskSnapshot("TEST", "RUNNING")), false))
                .isEqualTo("已发布");
        assertThat(RequirementStatusMachine.autoStatus(
                        "待需求评审", List.of(), false))
                .isEqualTo("待需求评审");
        assertThat(RequirementStatusMachine.autoStatus(
                        "待开发",
                        List.of(new TaskSnapshot("IMPLEMENT", "PENDING")),
                        false))
                .isEqualTo("待开发");
    }

    @Test
    void startGatesApplyOnlyToImplementAndBugfix() {
        List<String> blocked = RequirementStatusMachine.startBlockers("IMPLEMENT", OPEN_GATES);
        assertThat(blocked).hasSize(6);
        assertThat(RequirementStatusMachine.startBlockers("BUGFIX", READY_GATES)).isEmpty();
        assertThat(RequirementStatusMachine.startBlockers("TEST", OPEN_GATES)).isEmpty();
        assertThat(RequirementStatusMachine.startBlockers("DOCS", OPEN_GATES)).isEmpty();
        assertThat(RequirementStatusMachine.startBlockers("REVIEW", OPEN_GATES)).isEmpty();
        assertThat(READY_GATES.canStartImplementOrBugfix()).isTrue();
        assertThat(OPEN_GATES.canStartImplementOrBugfix()).isFalse();
    }

    @Test
    void stageIndexTracksLifecycle() {
        assertThat(RequirementStatusMachine.currentStageIndex("待分析", OPEN_GATES, List.of(), List.of()))
                .isEqualTo(1);
        assertThat(RequirementStatusMachine.currentStageIndex("待开发", OPEN_GATES, List.of(), List.of()))
                .isEqualTo(5);
        assertThat(RequirementStatusMachine.currentStageIndex("待开发", READY_GATES, List.of(), List.of()))
                .isEqualTo(7);
        assertThat(RequirementStatusMachine.currentStageIndex(
                        "开发中", READY_GATES, List.of(new TaskSnapshot("IMPLEMENT", "WAITING_APPROVAL")), List.of()))
                .isEqualTo(8);
        assertThat(RequirementStatusMachine.currentStageIndex(
                        "测试中", READY_GATES, List.of(), List.of(new DefectSnapshot("IN_FIX"))))
                .isEqualTo(11);
        assertThat(RequirementStatusMachine.currentStageIndex("待验收", READY_GATES, List.of(), List.of()))
                .isEqualTo(13);
        assertThat(RequirementStatusMachine.currentStageIndex("已发布", READY_GATES, List.of(), List.of()))
                .isEqualTo(14);
    }
}
