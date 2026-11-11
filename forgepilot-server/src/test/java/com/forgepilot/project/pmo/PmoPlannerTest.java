package com.forgepilot.project.pmo;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgepilot.project.lifecycle.RequirementStatusMachine.GateSnapshot;
import com.forgepilot.project.pmo.PmoSnapshot.DefectInfo;
import com.forgepilot.project.pmo.PmoSnapshot.TaskInfo;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PmoPlannerTest {

    private static final GateSnapshot READY = new GateSnapshot(true, true, true, true, true, true);
    private static final GateSnapshot NO_BINDINGS = new GateSnapshot(true, true, true, false, false, false);
    private static final GateSnapshot NO_REVIEWS = new GateSnapshot(false, false, false, true, true, true);

    @Test
    void confirmingAnalysisDoesNotProduceTaskDraft() {
        List<PmoCandidate> planned = PmoPlanner.plan(snapshot("待分析", READY, List.of(), List.of()));
        assertThat(planned).extracting(PmoCandidate::kind).containsExactly(PmoPlanner.KIND_CONFIRM_ANALYSIS);
        assertThat(planned).noneMatch(PmoCandidate::confirmCreatesTask);
        assertThat(planned.get(0).hint()).contains("不会立刻创建任务");
    }

    @Test
    void developmentGatesBlockCreateTaskAndSuggestBinding() {
        List<PmoCandidate> blocked = PmoPlanner.plan(snapshot("待开发", NO_BINDINGS, List.of(), List.of()));
        assertThat(blocked).extracting(PmoCandidate::kind).containsExactly(PmoPlanner.KIND_BIND_PROJECT);
        assertThat(blocked).noneMatch(PmoCandidate::confirmCreatesTask);
        assertThat(blocked.get(0).hint()).contains("请先到项目里绑定").contains("编码工具");
        assertThat(blocked.get(0).primaryActionLabel()).isEqualTo("去项目里绑定");

        List<PmoCandidate> reviewsMissing = PmoPlanner.plan(snapshot("待开发", NO_REVIEWS, List.of(), List.of()));
        assertThat(reviewsMissing).noneMatch(c -> PmoPlanner.KIND_CREATE_TASK.equals(c.kind()));
    }

    @Test
    void readyDevelopmentProposesImplementWithPrefillTitle() {
        List<PmoCandidate> planned = PmoPlanner.plan(snapshot("待开发", READY, List.of(), List.of()));
        assertThat(planned).hasSize(1);
        PmoCandidate draft = planned.get(0);
        assertThat(draft.kind()).isEqualTo(PmoPlanner.KIND_CREATE_TASK);
        assertThat(draft.taskType()).isEqualTo("IMPLEMENT");
        assertThat(draft.title()).isEqualTo("实现：登录页改造");
        assertThat(draft.confirmCreatesTask()).isTrue();
        assertThat(draft.hint()).contains("不会自动启动编码会话");
    }

    @Test
    void doesNotDuplicateOpenImplementOrTest() {
        List<PmoCandidate> withImplement = PmoPlanner.plan(snapshot(
                "开发中",
                READY,
                List.of(task("t-impl", "IMPLEMENT", "PENDING", null)),
                List.of()));
        assertThat(createTypes(withImplement)).isEmpty();

        List<PmoCandidate> withTest = PmoPlanner.plan(snapshot(
                "测试中",
                READY,
                List.of(task("t-test", "TEST", "RUNNING", null)),
                List.of()));
        assertThat(createTypes(withTest)).isEmpty();
    }

    @Test
    void completedImplementDoesNotBlockANewDraftAfterCancellationWindow() {
        List<PmoCandidate> planned = PmoPlanner.plan(snapshot(
                "待开发",
                READY,
                List.of(task("t-old", "IMPLEMENT", "CANCELLED", null)),
                List.of()));
        assertThat(createTypes(planned)).containsExactly("IMPLEMENT");
    }

    @Test
    void testDraftAfterImplementationAndInTesting() {
        List<PmoCandidate> afterApproval = PmoPlanner.plan(snapshot(
                "开发中",
                READY,
                List.of(task("t-impl", "IMPLEMENT", "WAITING_APPROVAL", null)),
                List.of()));
        assertThat(createTypes(afterApproval)).containsExactly("TEST");
        assertThat(afterApproval)
                .anyMatch(c -> PmoPlanner.KIND_REVIEW_CODE.equals(c.kind()) && "t-impl".equals(c.targetTaskId()));

        List<PmoCandidate> testing = PmoPlanner.plan(snapshot(
                "测试中",
                READY,
                List.of(task("t-impl", "IMPLEMENT", "COMPLETED", null)),
                List.of()));
        assertThat(createTypes(testing)).containsExactly("TEST");
    }

    @Test
    void runningImplementDoesNotProposeTestYet() {
        List<PmoCandidate> planned = PmoPlanner.plan(snapshot(
                "开发中",
                READY,
                List.of(task("t-impl", "IMPLEMENT", "RUNNING", null)),
                List.of()));
        assertThat(createTypes(planned)).isEmpty();
    }

    @Test
    void bugfixDraftsAreTiedToOpenDefectsWithoutOpenTask() {
        DefectInfo open = defect("d1", "DEF-1", "按钮错位", "OPEN");
        DefectInfo fixing = defect("d2", "DEF-2", "空指针", "IN_FIX");
        DefectInfo closed = defect("d3", "DEF-3", "已关", "CLOSED");
        List<PmoCandidate> planned = PmoPlanner.plan(snapshot(
                "测试中",
                READY,
                List.of(
                        task("t-impl", "IMPLEMENT", "COMPLETED", null),
                        task("t-fix-open", "BUGFIX", "RUNNING", "d2")),
                List.of(open, fixing, closed)));
        assertThat(planned)
                .filteredOn(PmoCandidate::confirmCreatesTask)
                .extracting(PmoCandidate::taskType, PmoCandidate::defectId, PmoCandidate::title)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("TEST", null, "测试：登录页改造"),
                        org.assertj.core.groups.Tuple.tuple("BUGFIX", "d1", "修复：DEF-1 按钮错位"));
    }

    @Test
    void completedBugfixForStillOpenDefectProducesANewDraft() {
        List<PmoCandidate> planned = PmoPlanner.plan(snapshot(
                "开发中",
                READY,
                List.of(
                        task("t-impl", "IMPLEMENT", "COMPLETED", null),
                        task("t-fix", "BUGFIX", "COMPLETED", "d1")),
                List.of(defect("d1", "DEF-1", "按钮错位", "OPEN"))));
        assertThat(planned)
                .filteredOn(c -> "BUGFIX".equals(c.taskType()))
                .extracting(PmoCandidate::defectId)
                .containsExactly("d1");
    }

    @Test
    void dismissalHidesUntilFingerprintChanges() {
        PmoSnapshot initial = snapshot("待开发", READY, List.of(), List.of());
        PmoCandidate first = onlyCreate(PmoPlanner.plan(initial));
        List<PmoCandidate> hidden = PmoPlanner.open(PmoPlanner.plan(initial), Set.of(first.proposalId()));
        assertThat(hidden).noneMatch(PmoCandidate::confirmCreatesTask);

        PmoSnapshot afterCancel = snapshot(
                "待开发", READY, List.of(task("t-old", "IMPLEMENT", "CANCELLED", null)), List.of());
        PmoCandidate again = onlyCreate(PmoPlanner.plan(afterCancel));
        assertThat(again.proposalId()).isNotEqualTo(first.proposalId());
        assertThat(PmoPlanner.open(List.of(again), Set.of(first.proposalId()))).containsExactly(again);

        PmoSnapshot statusChanged = snapshot("开发中", READY, List.of(), List.of());
        PmoCandidate moved = onlyCreate(PmoPlanner.plan(statusChanged));
        assertThat(moved.proposalId()).isNotEqualTo(first.proposalId());
        assertThat(PmoPlanner.open(List.of(moved), Set.of(first.proposalId()))).containsExactly(moved);

        PmoSnapshot newDefect = snapshot(
                "测试中",
                READY,
                List.of(task("t-impl", "IMPLEMENT", "COMPLETED", null)),
                List.of(defect("d-new", "DEF-9", "新缺陷", "OPEN")));
        List<PmoCandidate> withDefect = PmoPlanner.open(PmoPlanner.plan(newDefect), Set.of(first.proposalId()));
        assertThat(withDefect)
                .filteredOn(c -> "BUGFIX".equals(c.taskType()))
                .extracting(PmoCandidate::defectId)
                .containsExactly("d-new");
    }

    @Test
    void acceptanceAndReleaseRemainGuidanceAndDoNotCreateTasks() {
        List<PmoCandidate> accept = PmoPlanner.plan(snapshot("待验收", READY, List.of(), List.of()));
        assertThat(accept).extracting(PmoCandidate::kind).containsExactly(PmoPlanner.KIND_ACCEPT);
        assertThat(accept).noneMatch(PmoCandidate::confirmCreatesTask);

        List<PmoCandidate> release = PmoPlanner.plan(snapshot("已完成", READY, List.of(), List.of()));
        assertThat(release).extracting(PmoCandidate::kind).containsExactly(PmoPlanner.KIND_RELEASE);
        assertThat(release.get(0).hint()).contains("不会自动合并");
        assertThat(release).noneMatch(PmoCandidate::confirmCreatesTask);
    }

    @Test
    void fixedDefectSuggestsRegressionVerification() {
        List<PmoCandidate> planned = PmoPlanner.plan(snapshot(
                "测试中",
                READY,
                List.of(task("t-impl", "IMPLEMENT", "COMPLETED", null), task("t-test", "TEST", "COMPLETED", null)),
                List.of(defect("d1", "DEF-1", "按钮错位", "FIXED"))));
        assertThat(planned)
                .filteredOn(c -> PmoPlanner.KIND_VERIFY_REGRESSION.equals(c.kind()))
                .extracting(PmoCandidate::defectId)
                .containsExactly("d1");
        assertThat(createTypes(planned)).containsExactly("TEST");
    }

    @Test
    void proposalIdIsStableForTheSameFingerprint() {
        String fingerprint = "CREATE_TASK|IMPLEMENT|||待开发|";
        assertThat(PmoPlanner.proposalId(fingerprint)).isEqualTo(PmoPlanner.proposalId(fingerprint));
        assertThat(PmoPlanner.proposalId(fingerprint)).hasSize(64);
        assertThat(PmoPlanner.proposalId(fingerprint + "x")).isNotEqualTo(PmoPlanner.proposalId(fingerprint));
    }

    private static PmoCandidate onlyCreate(List<PmoCandidate> planned) {
        List<PmoCandidate> drafts = planned.stream().filter(PmoCandidate::confirmCreatesTask).toList();
        assertThat(drafts).hasSize(1);
        return drafts.get(0);
    }

    private static List<String> createTypes(List<PmoCandidate> planned) {
        return planned.stream()
                .filter(PmoCandidate::confirmCreatesTask)
                .map(PmoCandidate::taskType)
                .toList();
    }

    private static PmoSnapshot snapshot(
            String status, GateSnapshot gates, List<TaskInfo> tasks, List<DefectInfo> defects) {
        return new PmoSnapshot("r1", "REQ-1", "登录页改造", status, "p1", gates, tasks, defects);
    }

    private static TaskInfo task(String id, String type, String status, String defectId) {
        return new TaskInfo(id, type, status, defectId);
    }

    private static DefectInfo defect(String id, String code, String title, String status) {
        return new DefectInfo(id, code, title, status);
    }
}
