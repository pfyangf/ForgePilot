package com.forgepilot.project.pmo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.service.TaskService;
import com.forgepilot.execution.service.TaskService.TaskDetailView;
import com.forgepilot.execution.service.TaskService.TaskView;
import com.forgepilot.execution.web.CreateTaskRequest;
import com.forgepilot.project.domain.PmoProposalDecision;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.Requirement;
import com.forgepilot.project.lifecycle.RequirementStatusMachine.GateSnapshot;
import com.forgepilot.project.persistence.PmoProposalDecisionMapper;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.project.persistence.RequirementMapper;
import com.forgepilot.project.pmo.PmoProposalView.ConfirmResult;
import com.forgepilot.project.pmo.PmoSnapshot.TaskInfo;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.project.web.ConfirmProposalRequest;
import com.forgepilot.project.web.ReturnProposalRequest;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PmoProposalServiceTest {

    private static final String TENANT = "t1";
    private static final String USER = "u1";
    private static final String PROJECT = "p1";
    private static final String REQUIREMENT = "r1";
    private static final GateSnapshot READY = new GateSnapshot(true, true, true, true, true, true);

    private final RequirementMapper requirementMapper = mock(RequirementMapper.class);
    private final PmoProposalDecisionMapper decisionMapper = mock(PmoProposalDecisionMapper.class);
    private final PmoSnapshotAssembler assembler = mock(PmoSnapshotAssembler.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final ProjectMemberMapper projectMemberMapper = mock(ProjectMemberMapper.class);
    private final TaskService taskService = mock(TaskService.class);
    private final ActivityLogRecorder activityLogRecorder = mock(ActivityLogRecorder.class);
    private PmoProposalService service;

    @BeforeEach
    void setUp() {
        ProjectAccess access = new ProjectAccess(projectMapper, projectMemberMapper);
        service = new PmoProposalService(
                requirementMapper, decisionMapper, assembler, access, taskService, activityLogRecorder);
        when(projectMemberMapper.findProjectIdsByUserId(USER)).thenReturn(List.of(PROJECT));
        when(projectMapper.selectById(PROJECT)).thenReturn(project());
        when(requirementMapper.selectById(REQUIREMENT)).thenReturn(requirement("待开发"));
        when(assembler.tasksOf(REQUIREMENT)).thenReturn(List.of());
        when(decisionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(decisionMapper.insert(any(PmoProposalDecision.class))).thenReturn(1);
    }

    @Test
    void analysisStageListsGuidanceAndDoesNotCreateTasks() {
        when(requirementMapper.selectById(REQUIREMENT)).thenReturn(requirement("待分析"));
        when(assembler.assemble(any(Requirement.class), any(Project.class), any()))
                .thenReturn(snapshot("待分析", List.of()));

        PmoProposalView.ListView list = service.listForRequirement(user(), REQUIREMENT);

        assertThat(list.proposals()).extracting(PmoProposalView::kind).containsExactly(PmoPlanner.KIND_CONFIRM_ANALYSIS);
        assertThat(list.proposals()).noneMatch(PmoProposalView::confirmCreatesTask);
        verify(taskService, never()).create(any(), any(), any());
    }

    @Test
    void confirmCreatesPendingTaskAndDoesNotStartASession() {
        PmoSnapshot snap = snapshot("待开发", List.of());
        when(assembler.assemble(any(Requirement.class), any(Project.class), any())).thenReturn(snap);
        PmoCandidate draft = PmoPlanner.plan(snap).stream()
                .filter(PmoCandidate::confirmCreatesTask)
                .findFirst()
                .orElseThrow();
        when(taskService.create(any(), eq(REQUIREMENT), any(CreateTaskRequest.class)))
                .thenReturn(taskView("task-1", "PENDING", "实现：登录页改造"));

        ConfirmResult result =
                service.confirm(user(), REQUIREMENT, draft.proposalId(), new ConfirmProposalRequest("实现：登录页改造", null));

        assertThat(result.task().id()).isEqualTo("task-1");
        assertThat(result.task().status()).isEqualTo("PENDING");
        ArgumentCaptor<CreateTaskRequest> captor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(taskService).create(any(), eq(REQUIREMENT), captor.capture());
        assertThat(captor.getValue().taskType()).isEqualTo("IMPLEMENT");
        assertThat(captor.getValue().title()).isEqualTo("实现：登录页改造");
        verify(taskService, never()).get(any(), any());
        verifyNoMoreInteractions(taskService);
        ArgumentCaptor<PmoProposalDecision> decision = ArgumentCaptor.forClass(PmoProposalDecision.class);
        verify(decisionMapper).insert(decision.capture());
        assertThat(decision.getValue().getDecision()).isEqualTo("CONFIRMED");
        assertThat(decision.getValue().getCreatedTaskId()).isEqualTo("task-1");
        verify(activityLogRecorder)
                .record(
                        eq(TENANT),
                        eq("USER"),
                        eq(USER),
                        eq("PMO_PROPOSAL_CONFIRM"),
                        eq("REQUIREMENT"),
                        eq(REQUIREMENT),
                        eq(PROJECT),
                        eq(REQUIREMENT),
                        eq("task-1"),
                        any(),
                        eq("demo_admin 确认平台建议并创建任务：实现：登录页改造"),
                        any(),
                        eq("SUCCESS"));
    }

    @Test
    void confirmIsIdempotentAndDoesNotCreateASecondTask() {
        PmoSnapshot snap = snapshot("待开发", List.of());
        PmoCandidate draft = PmoPlanner.plan(snap).stream()
                .filter(PmoCandidate::confirmCreatesTask)
                .findFirst()
                .orElseThrow();
        PmoProposalDecision existing = new PmoProposalDecision();
        existing.setDecision("CONFIRMED");
        existing.setCreatedTaskId("task-1");
        existing.setProposalId(draft.proposalId());
        existing.setFingerprint(draft.fingerprint());
        existing.setKind(draft.kind());
        existing.setTaskType(draft.taskType());
        when(decisionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(existing));
        when(assembler.assemble(any(Requirement.class), any(Project.class), any())).thenReturn(snap);
        when(taskService.get(any(), eq("task-1")))
                .thenReturn(new TaskDetailView(
                        taskView("task-1", "PENDING", "实现：登录页改造"),
                        List.of(),
                        List.of(),
                        List.of(),
                        "",
                        null));

        ConfirmResult result = service.confirm(user(), REQUIREMENT, draft.proposalId(), new ConfirmProposalRequest(null, null));

        assertThat(result.task().id()).isEqualTo("task-1");
        verify(taskService, never()).create(any(), any(), any());
        verify(decisionMapper, never()).insert(any(PmoProposalDecision.class));
    }

    @Test
    void returningADraftHidesItUntilFingerprintChanges() {
        PmoSnapshot snap = snapshot("待开发", List.of());
        when(assembler.assemble(any(Requirement.class), any(Project.class), any())).thenReturn(snap);
        PmoCandidate draft = PmoPlanner.plan(snap).stream()
                .filter(PmoCandidate::confirmCreatesTask)
                .findFirst()
                .orElseThrow();

        PmoProposalView returned =
                service.dismiss(user(), REQUIREMENT, draft.proposalId(), new ReturnProposalRequest("先不拆"));
        assertThat(returned.proposalId()).isEqualTo(draft.proposalId());
        ArgumentCaptor<PmoProposalDecision> decision = ArgumentCaptor.forClass(PmoProposalDecision.class);
        verify(decisionMapper).insert(decision.capture());
        assertThat(decision.getValue().getDecision()).isEqualTo("RETURNED");
        assertThat(decision.getValue().getComment()).isEqualTo("先不拆");
        verify(taskService, never()).create(any(), any(), any());
        verify(activityLogRecorder)
                .record(
                        eq(TENANT),
                        eq("USER"),
                        eq(USER),
                        eq("PMO_PROPOSAL_RETURN"),
                        eq("REQUIREMENT"),
                        eq(REQUIREMENT),
                        eq(PROJECT),
                        eq(REQUIREMENT),
                        any(),
                        any(),
                        eq("demo_admin 退回平台建议：实现：登录页改造"),
                        any(),
                        eq("SUCCESS"));

        PmoProposalDecision saved = decision.getValue();
        when(decisionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(saved));
        PmoProposalView.ListView hidden = service.listForRequirement(user(), REQUIREMENT);
        assertThat(hidden.proposals()).noneMatch(PmoProposalView::confirmCreatesTask);
    }

    @Test
    void confirmRejectsGuidanceAndStaleFingerprints() {
        when(requirementMapper.selectById(REQUIREMENT)).thenReturn(requirement("待分析"));
        when(assembler.assemble(any(Requirement.class), any(Project.class), any()))
                .thenReturn(snapshot("待分析", List.of()));
        PmoCandidate analysis = PmoPlanner.plan(snapshot("待分析", List.of())).get(0);
        assertThatThrownBy(() -> service.confirm(user(), REQUIREMENT, analysis.proposalId(), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不能当作创建任务来确认");
        verify(taskService, never()).create(any(), any(), any());

        when(requirementMapper.selectById(REQUIREMENT)).thenReturn(requirement("待开发"));
        when(assembler.assemble(any(Requirement.class), any(Project.class), any()))
                .thenReturn(snapshot("待开发", List.of()));
        assertThatThrownBy(() -> service.confirm(user(), REQUIREMENT, "missing-id", null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void otherTenantCannotConfirm() {
        AuthUser other = new AuthUser("other", "other", "other", "t2", List.of("MEMBER"), List.of());
        when(projectMemberMapper.findProjectIdsByUserId("other")).thenReturn(List.of());
        assertThatThrownBy(() -> service.confirm(other, REQUIREMENT, "any", null))
                .isInstanceOf(ResponseStatusException.class);
        verify(taskService, never()).create(any(), any(), any());
    }

    private static AuthUser user() {
        return new AuthUser(USER, "demo_admin", "演示管理员", TENANT, List.of("MEMBER"), List.of("projects"));
    }

    private static Project project() {
        Project project = new Project();
        project.setId(PROJECT);
        project.setTenantId(TENANT);
        project.setName("支付");
        project.setCode("pay");
        project.setStatus("ACTIVE");
        return project;
    }

    private static Requirement requirement(String status) {
        Requirement requirement = new Requirement();
        requirement.setId(REQUIREMENT);
        requirement.setProjectId(PROJECT);
        requirement.setCode("REQ-1");
        requirement.setTitle("登录页改造");
        requirement.setStatus(status);
        requirement.setCreatedBy(USER);
        requirement.setOwnerId(USER);
        return requirement;
    }

    private static PmoSnapshot snapshot(String status, List<TaskInfo> tasks) {
        return new PmoSnapshot(REQUIREMENT, "REQ-1", "登录页改造", status, PROJECT, READY, tasks, List.of());
    }

    private static TaskView taskView(String id, String status, String title) {
        return new TaskView(
                id,
                "T-1",
                REQUIREMENT,
                "REQ-1",
                PROJECT,
                "支付",
                title,
                null,
                "IMPLEMENT",
                "MEDIUM",
                status,
                "fp/REQ-1",
                null,
                null,
                null,
                USER,
                USER,
                null,
                null,
                1);
    }
}
