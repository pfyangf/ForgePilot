package com.forgepilot.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.domain.TaskRepositoryFreeze;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.execution.persistence.TaskRepositoryFreezeMapper;
import com.forgepilot.execution.persistence.TaskStateHistoryMapper;
import com.forgepilot.delivery.persistence.MergeRequestMapper;
import com.forgepilot.execution.web.CreateTaskRequest;
import com.forgepilot.execution.web.TaskActionRequest;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.ProjectRepository;
import com.forgepilot.project.domain.Requirement;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.project.persistence.ProjectRepositoryMapper;
import com.forgepilot.project.persistence.RequirementMapper;
import com.forgepilot.project.lifecycle.RequirementLifecycleService;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TaskServiceTest {

    private static final String TENANT = "t1";
    private static final String USER = "u1";
    private static final String PROJECT = "p1";
    private static final String REQUIREMENT = "r1";

    private final CodingTaskMapper codingTaskMapper = mock(CodingTaskMapper.class);
    private final TaskRepositoryFreezeMapper freezeMapper = mock(TaskRepositoryFreezeMapper.class);
    private final TaskStateHistoryMapper historyMapper = mock(TaskStateHistoryMapper.class);
    private final ProjectRepositoryMapper projectRepositoryMapper = mock(ProjectRepositoryMapper.class);
    private final RequirementMapper requirementMapper = mock(RequirementMapper.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final ProjectMemberMapper projectMemberMapper = mock(ProjectMemberMapper.class);
    private final ActivityLogRecorder activityLogRecorder = mock(ActivityLogRecorder.class);
    private final MergeRequestMapper mergeRequestMapper = mock(MergeRequestMapper.class);
    private final RequirementLifecycleService lifecycleService = mock(RequirementLifecycleService.class);
    private TaskService service;

    @BeforeEach
    void setUp() {
        ProjectAccess access = new ProjectAccess(projectMapper, projectMemberMapper);
        service = new TaskService(
                codingTaskMapper,
                freezeMapper,
                historyMapper,
                projectRepositoryMapper,
                requirementMapper,
                projectMapper,
                access,
                activityLogRecorder,
                mergeRequestMapper,
                lifecycleService);
        when(projectMemberMapper.findProjectIdsByUserId(USER)).thenReturn(List.of(PROJECT));
        when(requirementMapper.selectById(REQUIREMENT)).thenReturn(requirement());
        when(projectMapper.selectById(PROJECT)).thenReturn(project());
        when(codingTaskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(codingTaskMapper.insert(any(CodingTask.class))).thenReturn(1);
        when(freezeMapper.insert(any(TaskRepositoryFreeze.class))).thenReturn(1);
        when(historyMapper.insert(any(com.forgepilot.execution.domain.TaskStateHistory.class))).thenReturn(1);
        when(projectRepositoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                repo("repo-api", "pay-api", "main"),
                repo("repo-web", "pay-web", "develop")));
        when(freezeMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> List.of());
        when(mergeRequestMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
    }

    @Test
    void createFreezesCurrentRepositoriesAndDoesNotCreateSession() {
        AuthUser user = user();
        when(freezeMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            // after create, get/list reads freezes; return empty for the first frozenRepos call inside create's toView
            return List.of();
        });

        TaskService.TaskView view = service.create(
                user, REQUIREMENT, new CreateTaskRequest("导出字段", "desc", "IMPLEMENT", "HIGH", USER, null));

        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(view.branchName()).isEqualTo("fp/REQ-PAY-1");
        ArgumentCaptor<TaskRepositoryFreeze> freeze = ArgumentCaptor.forClass(TaskRepositoryFreeze.class);
        verify(freezeMapper, times(2)).insert(freeze.capture());
        assertThat(freeze.getAllValues())
                .extracting(TaskRepositoryFreeze::getBaseBranch)
                .containsExactlyInAnyOrder("main", "develop");
        assertThat(freeze.getAllValues())
                .extracting(TaskRepositoryFreeze::getWorkingBranch)
                .containsOnly("fp/REQ-PAY-1");
        verify(activityLogRecorder)
                .record(eq(TENANT), eq("USER"), eq(USER), eq("TASK_CREATE"), eq("TASK"), any(), eq(PROJECT), eq(REQUIREMENT), any(), any(), any(), any(), eq("SUCCESS"));
    }

    @Test
    void createFailsWithoutRepositories() {
        when(projectRepositoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.create(
                        user(), REQUIREMENT, new CreateTaskRequest("x", null, "IMPLEMENT", "MEDIUM", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("尚未关联仓库");
    }

    @Test
    void bugfixRequiresDefectId() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "BUGFIX 任务必须关联缺陷"))
                .when(lifecycleService)
                .requireDefectForBugfix(REQUIREMENT, null);
        assertThatThrownBy(() -> service.create(
                        user(), REQUIREMENT, new CreateTaskRequest("修缺陷", null, "BUGFIX", "HIGH", USER, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("必须关联缺陷");
    }

    @Test
    void otherTenantCannotAccessTask() {
        CodingTask task = task("task-1", "PENDING");
        when(codingTaskMapper.selectById("task-1")).thenReturn(task);
        AuthUser other = new AuthUser("other", "other", "other", "t2", List.of("MEMBER"), List.of());
        when(projectMemberMapper.findProjectIdsByUserId("other")).thenReturn(List.of());
        when(projectMapper.selectById(PROJECT)).thenReturn(project());
        assertThatThrownBy(() -> service.get(other, "task-1")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void approveAndAbandonAreRealStateTransitions() {
        CodingTask waiting = task("task-a", "WAITING_APPROVAL");
        when(codingTaskMapper.selectById("task-a")).thenReturn(waiting);
        when(codingTaskMapper.updateById(any(CodingTask.class))).thenReturn(1);
        when(freezeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(projectRepositoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        TaskService.TaskDetailView approved =
                service.approve(user(), "task-a", new TaskActionRequest("lgtm"));
        assertThat(approved.task().status()).isEqualTo("COMPLETED");

        CodingTask blocked = task("task-b", "WAITING_USER");
        when(codingTaskMapper.selectById("task-b")).thenReturn(blocked);
        TaskService.TaskDetailView failed = service.abandon(user(), "task-b", new TaskActionRequest("stop"));
        assertThat(failed.task().status()).isEqualTo("FAILED");
    }

    private AuthUser user() {
        return new AuthUser(USER, USER, USER, TENANT, List.of("MEMBER"), List.of("projects"));
    }


    @Test
    void defaultWorkingBranchAddsTaskCodeWhenRequirementBranchTaken() {
        CodingTask existing = task("other", "PENDING");
        existing.setBranchName("fp/REQ-PAY-1");
        when(codingTaskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(codingTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(existing));
        TaskService.TaskView view = service.create(
                user(), REQUIREMENT, new CreateTaskRequest("第二项", null, "IMPLEMENT", "MEDIUM", null, null));
        assertThat(view.branchName()).isEqualTo("fp/REQ-PAY-1-TASK-PAY-2");
        ArgumentCaptor<TaskRepositoryFreeze> freeze = ArgumentCaptor.forClass(TaskRepositoryFreeze.class);
        verify(freezeMapper, org.mockito.Mockito.atLeast(2)).insert(freeze.capture());
        assertThat(freeze.getAllValues().stream().map(TaskRepositoryFreeze::getWorkingBranch).distinct().toList())
                .contains("fp/REQ-PAY-1-TASK-PAY-2");
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

    private Requirement requirement() {
        Requirement requirement = new Requirement();
        requirement.setId(REQUIREMENT);
        requirement.setProjectId(PROJECT);
        requirement.setStatus("待开发");
        requirement.setTitle("需求");
        requirement.setCode("REQ-PAY-1");
        return requirement;
    }

    private ProjectRepository repo(String id, String dir, String branch) {
        ProjectRepository repo = new ProjectRepository();
        repo.setId(id);
        repo.setProjectId(PROJECT);
        repo.setLocalDirectoryName(dir);
        repo.setDisplayName(dir);
        repo.setCloneUrl("https://example/" + dir + ".git");
        repo.setDefaultBranch(branch);
        repo.setProviderType("GITLAB");
        return repo;
    }

    private CodingTask task(String id, String status) {
        CodingTask task = new CodingTask();
        task.setId(id);
        task.setProjectId(PROJECT);
        task.setRequirementId(REQUIREMENT);
        task.setTitle(id);
        task.setStatus(status);
        task.setBranchName("fp/" + id);
        task.setCode("TASK-PAY-1");
        return task;
    }
}
