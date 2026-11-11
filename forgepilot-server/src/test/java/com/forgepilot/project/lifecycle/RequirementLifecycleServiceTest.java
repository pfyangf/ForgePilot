package com.forgepilot.project.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.project.domain.Defect;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.Requirement;
import com.forgepilot.project.domain.RequirementDocument;
import com.forgepilot.project.domain.RequirementRelease;
import com.forgepilot.project.domain.RequirementReleaseItem;
import com.forgepilot.project.domain.Review;
import com.forgepilot.project.persistence.AnalysisDraftMapper;
import com.forgepilot.project.persistence.DefectMapper;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.project.persistence.ProjectRepositoryMapper;
import com.forgepilot.project.persistence.RequirementDocumentMapper;
import com.forgepilot.project.persistence.RequirementMapper;
import com.forgepilot.project.persistence.RequirementReleaseItemMapper;
import com.forgepilot.project.persistence.RequirementReleaseMapper;
import com.forgepilot.project.persistence.ReviewMapper;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.project.web.AcceptRequirementRequest;
import com.forgepilot.project.web.CreateDefectRequest;
import com.forgepilot.project.web.CreateReleaseRequest;
import com.forgepilot.project.web.ReviewDecisionRequest;
import com.forgepilot.project.web.SaveDocumentRequest;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class RequirementLifecycleServiceTest {

    private static final String USER = "u1";
    private static final String PROJECT = "p1";
    private static final String REQ = "r1";

    private final RequirementMapper requirementMapper = mock(RequirementMapper.class);
    private final AnalysisDraftMapper analysisDraftMapper = mock(AnalysisDraftMapper.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final ProjectMemberMapper projectMemberMapper = mock(ProjectMemberMapper.class);
    private final ProjectRepositoryMapper projectRepositoryMapper = mock(ProjectRepositoryMapper.class);
    private final ReviewMapper reviewMapper = mock(ReviewMapper.class);
    private final RequirementDocumentMapper documentMapper = mock(RequirementDocumentMapper.class);
    private final DefectMapper defectMapper = mock(DefectMapper.class);
    private final RequirementReleaseMapper releaseMapper = mock(RequirementReleaseMapper.class);
    private final RequirementReleaseItemMapper releaseItemMapper = mock(RequirementReleaseItemMapper.class);
    private final CodingTaskMapper codingTaskMapper = mock(CodingTaskMapper.class);
    private final ActivityLogRecorder recorder = mock(ActivityLogRecorder.class);
    private RequirementLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new RequirementLifecycleService(
                requirementMapper,
                analysisDraftMapper,
                projectMapper,
                projectRepositoryMapper,
                reviewMapper,
                documentMapper,
                defectMapper,
                releaseMapper,
                releaseItemMapper,
                codingTaskMapper,
                new ProjectAccess(projectMapper, projectMemberMapper),
                recorder);
        when(projectMemberMapper.findProjectIdsByUserId(USER)).thenReturn(List.of(PROJECT));
        when(projectMapper.selectById(PROJECT)).thenReturn(project());
        when(requirementMapper.selectById(REQ)).thenReturn(requirement("待需求评审"));
        when(requirementMapper.updateById(any(Requirement.class))).thenReturn(1);
        when(reviewMapper.insert(any(Review.class))).thenReturn(1);
        when(reviewMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(documentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(documentMapper.insert(any(RequirementDocument.class))).thenReturn(1);
        when(documentMapper.updateById(any(RequirementDocument.class))).thenReturn(1);
        when(defectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(defectMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(defectMapper.insert(any(Defect.class))).thenReturn(1);
        when(defectMapper.updateById(any(Defect.class))).thenReturn(1);
        when(codingTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(projectRepositoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(releaseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(releaseMapper.insert(any(RequirementRelease.class))).thenReturn(1);
        when(releaseItemMapper.insert(any(RequirementReleaseItem.class))).thenReturn(1);
        when(analysisDraftMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
    }

    @Test
    void requirementReviewApproveAndReject() {
        service.reviewRequirement(user(), REQ, new ReviewDecisionRequest("APPROVED", "ok"));
        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("设计中");

        when(requirementMapper.selectById(REQ)).thenReturn(requirement("待需求评审"));
        service.reviewRequirement(user(), REQ, new ReviewDecisionRequest("REJECTED", "needs work"));
        verify(requirementMapper, org.mockito.Mockito.times(2)).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("待分析");
    }

    @Test
    void cannotWriteDesignBeforeRequirementReview() {
        when(requirementMapper.selectById(REQ)).thenReturn(requirement("待分析"));
        assertThatThrownBy(() -> service.saveDesignDocument(user(), REQ, new SaveDocumentRequest("设计", "# ui")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("需求评审");
    }

    @Test
    void designReviewRoundAndStartGates() {
        when(requirementMapper.selectById(REQ)).thenReturn(requirement("设计中"));
        when(reviewMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            Review review = new Review();
            review.setId("rv-req");
            review.setSubjectType("REQUIREMENT");
            review.setSubjectId(REQ);
            review.setDecision("APPROVED");
            return List.of(review);
        });
        service.saveDesignDocument(user(), REQ, new SaveDocumentRequest("乘法表设计", "# layout"));
        ArgumentCaptor<RequirementDocument> docs = ArgumentCaptor.forClass(RequirementDocument.class);
        verify(documentMapper).insert(docs.capture());
        assertThat(docs.getValue().getVersionNo()).isEqualTo(1);
        assertThat(docs.getValue().getCurrentFlag()).isTrue();

        RequirementDocument current = docs.getValue();
        when(documentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(current));
        service.submitDesignReview(user(), REQ);
        ArgumentCaptor<Requirement> req = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper, org.mockito.Mockito.atLeastOnce()).updateById(req.capture());
        assertThat(req.getValue().getStatus()).isEqualTo("待设计评审");

        when(requirementMapper.selectById(REQ)).thenReturn(requirement("待设计评审"));
        service.reviewDesignDocument(user(), REQ, new ReviewDecisionRequest("APPROVED", "lgtm"));
        verify(requirementMapper, org.mockito.Mockito.atLeastOnce()).updateById(req.capture());
        assertThat(req.getValue().getStatus()).isEqualTo("待开发");

        when(requirementMapper.selectById(REQ)).thenReturn(requirement("待开发"));
        project().setDefaultAgentProfileId("agent-1");
        when(projectMapper.selectById(PROJECT)).thenReturn(boundProject());
        when(reviewMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            Review review = new Review();
            review.setDecision("APPROVED");
            return List.of(review);
        });
        service.assertCanStart(REQ, "IMPLEMENT");
        service.assertCanStart(REQ, "TEST");

        when(projectMapper.selectById(PROJECT)).thenReturn(project());
        assertThatThrownBy(() -> service.assertCanStart(REQ, "IMPLEMENT"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("开工门禁");
        service.assertCanStart(REQ, "DOCS");
    }

    @Test
    void bugfixLinkageAndAcceptanceAndRelease() {
        when(requirementMapper.selectById(REQ)).thenReturn(requirement("测试中"));
        var created = service.createDefect(user(), REQ, new CreateDefectRequest("算式错误", "3x3", "HIGH", null));
        ArgumentCaptor<Defect> captor = ArgumentCaptor.forClass(Defect.class);
        verify(defectMapper).insert(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("DEF-PAY-1");
        assertThat(captor.getValue().getStatus()).isEqualTo("OPEN");
        assertThat(created.code()).isEqualTo("DEF-PAY-1");

        Defect defect = new Defect();
        defect.setId("d1");
        defect.setRequirementId(REQ);
        defect.setProjectId(PROJECT);
        defect.setCode("DEF-PAY-1");
        defect.setStatus("OPEN");
        when(defectMapper.selectById("d1")).thenReturn(defect);

        CodingTask task = new CodingTask();
        task.setId("t1");
        task.setRequirementId(REQ);
        task.setTaskType("BUGFIX");
        task.setDefectId("d1");
        service.onDevelopmentStarted(task);
        assertThat(defect.getStatus()).isEqualTo("IN_FIX");

        service.onTaskCompleted(task);
        assertThat(defect.getStatus()).isEqualTo("FIXED");

        service.verifyDefect(user(), "d1", null);
        assertThat(defect.getStatus()).isEqualTo("VERIFIED");

        when(defectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(defect));
        when(requirementMapper.selectById(REQ)).thenReturn(requirement("待验收"));
        var accepted = service.accept(user(), REQ, new AcceptRequirementRequest(true, "ok"));
        assertThat(accepted.status()).isEqualTo("已完成");

        when(requirementMapper.selectById(REQ)).thenReturn(requirement("已完成"));
        var release = service.createRelease(user(), PROJECT, new CreateReleaseRequest("1.0.0", "first", List.of(REQ)));
        assertThat(release.version()).isEqualTo("1.0.0");
        ArgumentCaptor<Requirement> req = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper, org.mockito.Mockito.atLeastOnce()).updateById(req.capture());
        assertThat(req.getAllValues().stream().anyMatch(r -> "已发布".equals(r.getStatus()))).isTrue();
    }

    @Test
    void openDefectsBlockAcceptanceAndReleaseRequiresCompleted() {
        Defect open = new Defect();
        open.setId("d1");
        open.setRequirementId(REQ);
        open.setStatus("OPEN");
        when(defectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(open));
        when(requirementMapper.selectById(REQ)).thenReturn(requirement("待验收"));
        assertThatThrownBy(() -> service.accept(user(), REQ, new AcceptRequirementRequest(true, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("未关闭的缺陷");

        when(requirementMapper.selectById(REQ)).thenReturn(requirement("待验收"));
        assertThatThrownBy(() -> service.createRelease(
                        user(), PROJECT, new CreateReleaseRequest("1.0.0", null, List.of(REQ))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("尚未验收完成");
    }

    @Test
    void requireDefectForBugfix() {
        assertThatThrownBy(() -> service.requireDefectForBugfix(REQ, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("必须关联缺陷");
    }

    private AuthUser user() {
        return new AuthUser(USER, USER, USER, "t1", List.of("MEMBER"), List.of("projects"));
    }

    private Project project() {
        Project project = new Project();
        project.setId(PROJECT);
        project.setTenantId("t1");
        project.setName("支付");
        project.setCode("pay");
        project.setStatus("ACTIVE");
        return project;
    }

    private Project boundProject() {
        Project project = project();
        project.setDefaultAgentProfileId("agent-1");
        project.setDefaultModelProviderId("prov-1");
        project.setDefaultModelId("gpt");
        return project;
    }

    private Requirement requirement(String status) {
        Requirement requirement = new Requirement();
        requirement.setId(REQ);
        requirement.setProjectId(PROJECT);
        requirement.setCode("REQ-PAY-1");
        requirement.setTitle("九九乘法表页面");
        requirement.setStatus(status);
        requirement.setStatusLocked("已完成".equals(status) || "已发布".equals(status) || "已取消".equals(status));
        return requirement;
    }
}
