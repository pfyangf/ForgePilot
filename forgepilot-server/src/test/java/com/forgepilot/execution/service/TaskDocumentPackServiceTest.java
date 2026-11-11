package com.forgepilot.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.domain.SessionConventionSnapshot;
import com.forgepilot.execution.persistence.AgentSessionMapper;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.execution.persistence.SessionConventionSnapshotMapper;
import com.forgepilot.execution.service.TaskDocumentPackService.DocumentPack;
import com.forgepilot.project.domain.AnalysisDraft;
import com.forgepilot.project.domain.Defect;
import com.forgepilot.project.domain.Requirement;
import com.forgepilot.project.domain.RequirementDocument;
import com.forgepilot.project.domain.Review;
import com.forgepilot.project.persistence.AnalysisDraftMapper;
import com.forgepilot.project.persistence.DefectMapper;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.project.persistence.RequirementDocumentMapper;
import com.forgepilot.project.persistence.RequirementMapper;
import com.forgepilot.project.persistence.ReviewMapper;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.service.GuidanceService;
import com.forgepilot.system.service.GuidanceService.EffectiveConventions;
import com.forgepilot.system.service.GuidanceService.EffectiveItem;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TaskDocumentPackServiceTest {

    private final CodingTaskMapper codingTaskMapper = mock(CodingTaskMapper.class);
    private final RequirementMapper requirementMapper = mock(RequirementMapper.class);
    private final AnalysisDraftMapper analysisDraftMapper = mock(AnalysisDraftMapper.class);
    private final RequirementDocumentMapper documentMapper = mock(RequirementDocumentMapper.class);
    private final ReviewMapper reviewMapper = mock(ReviewMapper.class);
    private final DefectMapper defectMapper = mock(DefectMapper.class);
    private final AgentSessionMapper agentSessionMapper = mock(AgentSessionMapper.class);
    private final SessionConventionSnapshotMapper snapshotMapper = mock(SessionConventionSnapshotMapper.class);
    private final GuidanceService guidanceService = mock(GuidanceService.class);
    private final ProjectMemberMapper projectMemberMapper = mock(ProjectMemberMapper.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private TaskDocumentPackService service;

    @BeforeEach
    void setUp() {
        when(projectMemberMapper.findProjectIdsByUserId("u1")).thenReturn(List.of("p1"));
        service = new TaskDocumentPackService(
                codingTaskMapper,
                requirementMapper,
                analysisDraftMapper,
                documentMapper,
                reviewMapper,
                defectMapper,
                agentSessionMapper,
                snapshotMapper,
                guidanceService,
                new ProjectAccess(projectMapper, projectMemberMapper));
        com.forgepilot.project.domain.Project project = new com.forgepilot.project.domain.Project();
        project.setId("p1");
        project.setTenantId("t1");
        when(projectMapper.selectById("p1")).thenReturn(project);
    }

    @Test
    void assembleIncludesTaskRelatedDocumentsAndDefectForBugfix() {
        CodingTask task = task("BUGFIX", "d1");
        when(requirementMapper.selectById("r1")).thenReturn(requirement());
        when(analysisDraftMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(analysis());
        when(documentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(design());
        when(documentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(design()));
        when(reviewMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(review("REQUIREMENT", "APPROVED", "需求可以通过"), review("DESIGN_DOCUMENT", "APPROVED", "设计可以通过")));
        when(guidanceService.composeForProject(any(), eq("p1")))
                .thenReturn(new EffectiveConventions(
                        "p1",
                        "t1",
                        List.of(new EffectiveItem("PROJECT", "编码约定", "使用 UTF-8", "g1"))));
        Defect defect = new Defect();
        defect.setId("d1");
        defect.setCode("DEF-DEMO99-2");
        defect.setTitle("表头截断");
        defect.setDescription("打印预览里表头被截断");
        defect.setSeverity("MEDIUM");
        defect.setStatus("OPEN");
        when(defectMapper.selectById("d1")).thenReturn(defect);

        DocumentPack pack = service.assemble(user(), task);

        assertThat(pack.items())
                .extracting(item -> item.kind())
                .contains(
                        TaskDocumentPackService.TASK_BRIEF,
                        TaskDocumentPackService.REQUIREMENT_BODY,
                        TaskDocumentPackService.ANALYSIS,
                        TaskDocumentPackService.DESIGN_DOC,
                        TaskDocumentPackService.REVIEW_CONCLUSIONS,
                        TaskDocumentPackService.CONVENTION,
                        TaskDocumentPackService.GUIDANCE,
                        TaskDocumentPackService.DEFECT);
        assertThat(pack.items())
                .filteredOn(item -> TaskDocumentPackService.REQUIREMENT_BODY.equals(item.kind()))
                .extracting(item -> item.body())
                .first()
                .asString()
                .contains("九九乘法表")
                .contains("浏览器直接打开");
        assertThat(pack.items())
                .filteredOn(item -> TaskDocumentPackService.ANALYSIS.equals(item.kind()))
                .extracting(item -> item.body())
                .first()
                .asString()
                .contains("已确认的分析");
        assertThat(pack.items())
                .filteredOn(item -> TaskDocumentPackService.DEFECT.equals(item.kind()))
                .extracting(item -> item.body())
                .first()
                .asString()
                .contains("DEF-DEMO99-2")
                .contains("表头截断");
        String prompt = service.toPrompt(pack, "fp/REQ-DEMO99-3", "注意打印");
        assertThat(prompt)
                .contains("不要另起分支名")
                .contains("fp/REQ-DEMO99-3")
                .contains("而不只是任务说明")
                .contains("已确认的分析")
                .contains("使用 UTF-8")
                .contains("注意打印");
    }

    @Test
    void persistThenLoadFrozenKeepsBodiesAfterSourceChanges() {
        CodingTask task = task("IMPLEMENT", null);
        when(requirementMapper.selectById("r1")).thenReturn(requirement());
        when(analysisDraftMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(analysis());
        when(documentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(design());
        when(documentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(reviewMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(guidanceService.composeForProject(any(), eq("p1")))
                .thenReturn(new EffectiveConventions("p1", "t1", List.of()));

        DocumentPack live = service.assemble(user(), task);
        List<SessionConventionSnapshot> stored = new ArrayList<>();
        when(snapshotMapper.insert(any(SessionConventionSnapshot.class))).thenAnswer(inv -> {
            stored.add(inv.getArgument(0));
            return 1;
        });
        service.persist("session-1", live);
        verify(snapshotMapper, org.mockito.Mockito.atLeastOnce()).insert(any(SessionConventionSnapshot.class));
        ArgumentCaptor<SessionConventionSnapshot> captor = ArgumentCaptor.forClass(SessionConventionSnapshot.class);
        verify(snapshotMapper, org.mockito.Mockito.atLeast(5)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(SessionConventionSnapshot::getKind)
                .contains(TaskDocumentPackService.TASK_BRIEF, TaskDocumentPackService.REQUIREMENT_BODY);

        when(snapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(stored);
        // 源需求被改写，冻结包不应跟着变
        Requirement changed = requirement();
        changed.setDescription("后来改过的需求，不应进入已启动会话");
        when(requirementMapper.selectById("r1")).thenReturn(changed);

        DocumentPack frozen = service.loadFrozen("session-1");
        assertThat(frozen.items())
                .filteredOn(item -> TaskDocumentPackService.REQUIREMENT_BODY.equals(item.kind()))
                .extracting(item -> item.body())
                .first()
                .asString()
                .contains("浏览器直接打开")
                .doesNotContain("后来改过的需求");
    }

    private static String eqProject() {
        return "p1";
    }

    private static AuthUser user() {
        return new AuthUser("u1", "demo", "demo", "t1", List.of("MEMBER"), List.of("dashboard"));
    }

    private static CodingTask task(String type, String defectId) {
        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setProjectId("p1");
        task.setRequirementId("r1");
        task.setCode("TASK-DEMO99-7");
        task.setTitle("实现页面");
        task.setDescription("按设计文档实现乘法表页面");
        task.setTaskType(type);
        task.setDefectId(defectId);
        return task;
    }

    private static Requirement requirement() {
        Requirement requirement = new Requirement();
        requirement.setId("r1");
        requirement.setCode("REQ-DEMO99-3");
        requirement.setTitle("九九乘法表验收补充");
        requirement.setDescription("做一个可在浏览器直接打开的九九乘法表页面");
        requirement.setAcceptance("算式正确");
        return requirement;
    }

    private static AnalysisDraft analysis() {
        AnalysisDraft draft = new AnalysisDraft();
        draft.setId("a1");
        draft.setBody("已确认的分析：先做页面再补测试");
        draft.setConfirmedAt(Instant.parse("2026-11-01T00:00:00Z"));
        return draft;
    }

    private static RequirementDocument design() {
        RequirementDocument document = new RequirementDocument();
        document.setId("doc-1");
        document.setTitle("页面设计说明");
        document.setVersionNo(2);
        document.setCurrentFlag(true);
        document.setKind("DESIGN");
        document.setBody("版式与配色要求 v2");
        return document;
    }

    private static Review review(String type, String decision, String comment) {
        Review review = new Review();
        review.setId(type + "-1");
        review.setSubjectType(type);
        review.setSubjectId("REQUIREMENT".equals(type) ? "r1" : "doc-1");
        review.setDecision(decision);
        review.setComment(comment);
        review.setCreatedAt(Instant.parse("2026-11-02T00:00:00Z"));
        return review;
    }
}
