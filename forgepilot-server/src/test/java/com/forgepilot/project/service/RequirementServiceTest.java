package com.forgepilot.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.execution.service.TaskService;
import com.forgepilot.project.domain.AnalysisDraft;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.Requirement;
import com.forgepilot.project.lifecycle.RequirementLifecycleService;
import com.forgepilot.project.lifecycle.RequirementLifecycleService.GateView;
import com.forgepilot.project.lifecycle.RequirementLifecycleService.LifecycleBundle;
import com.forgepilot.project.persistence.AnalysisDraftMapper;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.project.persistence.RequirementMapper;
import com.forgepilot.project.web.ConfirmAnalysisRequest;
import com.forgepilot.project.web.CreateRequirementRequest;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class RequirementServiceTest {

    private static final String TENANT = "t1";
    private static final String USER = "u1";
    private static final String PROJECT = "p1";

    private final RequirementMapper requirementMapper = mock(RequirementMapper.class);
    private final AnalysisDraftMapper analysisDraftMapper = mock(AnalysisDraftMapper.class);
    private final CodingTaskMapper codingTaskMapper = mock(CodingTaskMapper.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final ProjectMemberMapper projectMemberMapper = mock(ProjectMemberMapper.class);
    private final ActivityLogRecorder activityLogRecorder = mock(ActivityLogRecorder.class);
    private final TaskService taskService = mock(TaskService.class);
    private final RequirementLifecycleService lifecycleService = mock(RequirementLifecycleService.class);
    private RequirementService service;

    @BeforeEach
    void setUp() {
        ProjectAccess access = new ProjectAccess(projectMapper, projectMemberMapper);
        service = new RequirementService(
                requirementMapper,
                analysisDraftMapper,
                codingTaskMapper,
                access,
                activityLogRecorder,
                taskService,
                lifecycleService);
        when(projectMemberMapper.findProjectIdsByUserId(USER)).thenReturn(List.of(PROJECT));
        when(projectMapper.selectById(PROJECT)).thenReturn(project());
        when(requirementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(requirementMapper.insert(any(Requirement.class))).thenReturn(1);
        when(codingTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(analysisDraftMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(lifecycleService.bundle(any())).thenReturn(emptyBundle());
        when(taskService.listByRequirement(any(), any())).thenReturn(List.of());
    }

    @Test
    void createRequirementInMemberProject() {
        AuthUser user = user();
        RequirementService.RequirementView view = service.create(
                user, PROJECT, new CreateRequirementRequest("优惠券", "desc", null, null, null, "HIGH", null));
        assertThat(view.title()).isEqualTo("优惠券");
        assertThat(view.status()).isEqualTo("待分析");
        assertThat(view.code()).startsWith("REQ-PAY-");
        verify(activityLogRecorder)
                .record(eq(TENANT), eq("USER"), eq(USER), eq("REQUIREMENT_CREATE"), eq("REQUIREMENT"), any(), eq(PROJECT), any(), any(), any(), any(), any(), eq("SUCCESS"));
    }

    @Test
    void confirmAnalysisGoesToRequirementReviewAndKeepsSuggestedTasks() {
        Requirement requirement = requirement("r1", "待分析");
        when(requirementMapper.selectById("r1")).thenReturn(requirement);
        when(requirementMapper.updateById(any(Requirement.class))).thenReturn(1);
        when(analysisDraftMapper.insert(any(AnalysisDraft.class))).thenReturn(1);
        when(analysisDraftMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            AnalysisDraft draft = new AnalysisDraft();
            draft.setId("d1");
            draft.setRequirementId("r1");
            draft.setBody("confirmed body");
            draft.setSuggestedTasks("[\"改 api\",\"改 web\"]");
            draft.setConfirmedBy(USER);
            draft.setConfirmedAt(java.time.Instant.now());
            return List.of(draft);
        });

        RequirementService.RequirementDetailView detail = service.confirmAnalysis(
                user(), "r1", new ConfirmAnalysisRequest("confirmed body", List.of("改 api", "改 web")));

        assertThat(detail.requirement().status()).isEqualTo("待需求评审");
        assertThat(detail.confirmed()).isTrue();
        assertThat(detail.suggestedTaskTitles()).containsExactly("改 api", "改 web");
        verify(taskService, never()).create(any(), any(), any());
        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("待需求评审");
        verify(activityLogRecorder)
                .record(eq(TENANT), eq("USER"), eq(USER), eq("REQUIREMENT_CONFIRM_ANALYSIS"), eq("REQUIREMENT"), eq("r1"), eq(PROJECT), eq("r1"), any(), any(), any(), any(), eq("SUCCESS"));
    }

    @Test
    void foreignTenantCannotConfirm() {
        when(requirementMapper.selectById("r1")).thenReturn(requirement("r1", "待分析"));
        AuthUser other = new AuthUser("other", "other", "other", "t2", List.of("MEMBER"), List.of());
        when(projectMemberMapper.findProjectIdsByUserId("other")).thenReturn(List.of());
        assertThatThrownBy(() -> service.confirmAnalysis(
                        other, "r1", new ConfirmAnalysisRequest("x", List.of())))
                .isInstanceOf(ResponseStatusException.class);
    }

    private AuthUser user() {
        return new AuthUser(USER, USER, USER, TENANT, List.of("MEMBER"), List.of("projects"));
    }

    private Project project() {
        Project project = new Project();
        project.setId(PROJECT);
        project.setTenantId(TENANT);
        project.setName("支付");
        project.setCode("pay");
        project.setStatus("ACTIVE");
        return project;
    }

    private Requirement requirement(String id, String status) {
        Requirement requirement = new Requirement();
        requirement.setId(id);
        requirement.setProjectId(PROJECT);
        requirement.setTitle("优惠券");
        requirement.setStatus(status);
        requirement.setCode("REQ-PAY-1");
        return requirement;
    }

    private static LifecycleBundle emptyBundle() {
        GateView gates = new GateView(
                false, false, false, false, false, false, false, List.of(), 1, "需求分析", null, null);
        return new LifecycleBundle(List.of(), List.of(), List.of(), List.of(), List.of(), gates);
    }
}
