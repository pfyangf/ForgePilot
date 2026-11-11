package com.forgepilot.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.delivery.git.InMemoryGitCredentialService;
import com.forgepilot.delivery.git.LocalGitSupport;
import com.forgepilot.delivery.git.MultiRepoWorkspaceCheckout;
import com.forgepilot.execution.domain.AgentEvent;
import com.forgepilot.execution.domain.AgentSession;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.domain.SessionConventionSnapshot;
import com.forgepilot.execution.domain.TaskRepositoryFreeze;
import com.forgepilot.execution.domain.TaskStateHistory;
import com.forgepilot.execution.domain.Workspace;
import com.forgepilot.execution.persistence.AgentEventMapper;
import com.forgepilot.execution.persistence.AgentSessionMapper;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.execution.persistence.SessionConventionSnapshotMapper;
import com.forgepilot.execution.persistence.TaskRepositoryFreezeMapper;
import com.forgepilot.execution.persistence.TaskStateHistoryMapper;
import com.forgepilot.execution.persistence.WorkspaceMapper;
import com.forgepilot.execution.runtime.AgentRuntimePort;
import com.forgepilot.execution.stream.InMemorySessionLogStream;
import com.forgepilot.execution.persistence.ExecutionNodeMapper;
import com.forgepilot.execution.web.TaskActionRequest;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.ProjectRepository;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.project.persistence.ProjectRepositoryMapper;
import com.forgepilot.project.lifecycle.RequirementLifecycleService;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.outbox.InMemoryControlEventPublisher;
import com.forgepilot.system.outbox.OutboxService;
import com.forgepilot.system.persistence.OutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.domain.ModelProvider;
import com.forgepilot.system.persistence.IntegrationSecretMapper;
import com.forgepilot.system.persistence.ModelProviderMapper;
import com.forgepilot.system.service.GuidanceService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskSessionServiceTest {

    @TempDir
    Path temp;

    private final CodingTaskMapper codingTaskMapper = mock(CodingTaskMapper.class);
    private final AgentSessionMapper agentSessionMapper = mock(AgentSessionMapper.class);
    private final AgentEventMapper agentEventMapper = mock(AgentEventMapper.class);
    private final SessionConventionSnapshotMapper sessionConventionSnapshotMapper =
            mock(SessionConventionSnapshotMapper.class);
    private final TaskRepositoryFreezeMapper freezeMapper = mock(TaskRepositoryFreezeMapper.class);
    private final ProjectRepositoryMapper projectRepositoryMapper = mock(ProjectRepositoryMapper.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final ProjectMemberMapper projectMemberMapper = mock(ProjectMemberMapper.class);
    private final WorkspaceMapper workspaceMapper = mock(WorkspaceMapper.class);
    private final TaskStateHistoryMapper historyMapper = mock(TaskStateHistoryMapper.class);
    private final ModelProviderMapper modelProviderMapper = mock(ModelProviderMapper.class);
    private final IntegrationSecretMapper integrationSecretMapper = mock(IntegrationSecretMapper.class);
    private final GuidanceService guidanceService = mock(GuidanceService.class);
    private final AgentRuntimePort agentRuntimePort = mock(AgentRuntimePort.class);
    private final ActivityLogRecorder recorder = mock(ActivityLogRecorder.class);
    private final ExecutionNodeMapper executionNodeMapper = mock(ExecutionNodeMapper.class);

    private TaskSessionService service;
    private Path remote;

    @BeforeEach
    void setUp() throws Exception {
        LocalGitSupport git = new LocalGitSupport();
        remote = initBare(git, temp.resolve("r.git"));
        when(projectMemberMapper.findProjectIdsByUserId("u1")).thenReturn(List.of("p1"));
        service = new TaskSessionService(
                codingTaskMapper,
                agentSessionMapper,
                agentEventMapper,
                sessionConventionSnapshotMapper,
                freezeMapper,
                projectRepositoryMapper,
                projectMapper,
                workspaceMapper,
                historyMapper,
                modelProviderMapper,
                integrationSecretMapper,
                new ProjectAccess(projectMapper, projectMemberMapper),
                guidanceService,
                new MultiRepoWorkspaceCheckout(git),
                agentRuntimePort,
                recorder,
                new NodeRegistryService(
                        executionNodeMapper,
                        codingTaskMapper,
                        recorder,
                        new OutboxService(mock(OutboxMapper.class), new ObjectMapper(), new InMemoryControlEventPublisher()),
                        45),
                new InMemorySessionLogStream(),
                new OutboxService(mock(OutboxMapper.class), new ObjectMapper(), new InMemoryControlEventPublisher()),
                new InMemoryGitCredentialService(900),
                mock(RequirementLifecycleService.class),
                temp.resolve("storage").toString());
        when(executionNodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        Project project = new Project();
        project.setId("p1");
        project.setTenantId("t1");
        project.setName("支付");
        project.setCode("pay");
        project.setStatus("ACTIVE");
        project.setDefaultModelProviderId("prov1");
        project.setDefaultModelId("gpt");
        when(projectMapper.selectById("p1")).thenReturn(project);

        CodingTask task = task("PENDING");
        when(codingTaskMapper.selectById("task-1")).thenReturn(task);
        when(codingTaskMapper.updateById(any(CodingTask.class))).thenReturn(1);
        when(historyMapper.insert(any(TaskStateHistory.class))).thenReturn(1);
        when(agentSessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(agentSessionMapper.insert(any(AgentSession.class))).thenReturn(1);
        when(agentSessionMapper.updateById(any(AgentSession.class))).thenReturn(1);
        when(agentEventMapper.insert(any(AgentEvent.class))).thenReturn(1);
        when(sessionConventionSnapshotMapper.insert(any(SessionConventionSnapshot.class))).thenReturn(1);
        when(workspaceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(workspaceMapper.insert(any(Workspace.class))).thenReturn(1);

        TaskRepositoryFreeze freeze = new TaskRepositoryFreeze();
        freeze.setId("f1");
        freeze.setTaskId("task-1");
        freeze.setProjectRepositoryId("repoA");
        freeze.setBaseBranch("master");
        freeze.setDeliveryState("NONE");
        when(freezeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(freeze));

        ProjectRepository repo = new ProjectRepository();
        repo.setId("repoA");
        repo.setProjectId("p1");
        repo.setLocalDirectoryName("api");
        repo.setCloneUrl(remote.toUri().toString());
        repo.setDefaultBranch("master");
        repo.setProviderType("GITLAB");
        when(projectRepositoryMapper.selectById("repoA")).thenReturn(repo);

        ModelProvider provider = new ModelProvider();
        provider.setId("prov1");
        provider.setEnabled(true);
        provider.setBaseUrl("https://api.openai.com/v1");
        provider.setUpstreamWireApi("responses");
        provider.setSecretId(null);
        when(modelProviderMapper.selectById("prov1")).thenReturn(provider);

        when(guidanceService.snapshotForProject(any(), anyString()))
                .thenReturn(new GuidanceService.CompositionSnapshotView(
                        "snap1",
                        "p1",
                        "hash1",
                        List.of(new GuidanceService.EffectiveItem("PROJECT", "规则", "内容", "g1")),
                        Instant.now()));
        when(agentRuntimePort.tailLogs(anyString(), anyInt())).thenReturn(List.of("log"));
    }

    @Test
    void startCreatesSessionDistinctFromTaskAndFailsCleanlyWhenRuntimeUnavailable() {
        when(agentRuntimePort.start(any())).thenReturn(new AgentRuntimePort.StartResult(
                "session-x",
                "FAILED",
                null,
                null,
                Map.of(),
                false,
                "NOT_AVAILABLE",
                "本机未找到 codex CLI（NOT_AVAILABLE）"));

        AtomicReference<AgentSession> inserted = new AtomicReference<>();
        when(agentSessionMapper.insert(any(AgentSession.class))).thenAnswer(inv -> {
            inserted.set(inv.getArgument(0));
            return 1;
        });

        AuthUser user = new AuthUser("u1", "m", "成员", "t1", List.of("MEMBER"), List.of());
        var result = service.start(user, "task-1", new TaskActionRequest("go"));
        assertThat(result.taskStatus()).isEqualTo("WAITING_USER");
        assertThat(result.sessionStatus()).isEqualTo("FAILED");
        assertThat(result.message()).contains("NOT_AVAILABLE");
        assertThat(inserted.get().getTaskId()).isEqualTo("task-1");
        assertThat(inserted.get().getId()).isNotEqualTo("task-1");
        verify(agentRuntimePort).revokeModelToken(anyString());
        // never completed
        assertThat(result.taskStatus()).isNotEqualTo("COMPLETED");
    }

    @Test
    void retryReusesWorkspaceAndDoesNotClaimSuccessWithoutRuntime() throws Exception {
        when(agentRuntimePort.start(any())).thenReturn(new AgentRuntimePort.StartResult(
                "s1", "FAILED", null, null, Map.of(), false, "NOT_AVAILABLE", "no codex"));
        AuthUser user = new AuthUser("u1", "m", "成员", "t1", List.of("MEMBER"), List.of());
        service.start(user, "task-1", new TaskActionRequest("first"));
        Path checkout = temp.resolve("storage/workspaces/task-1/api");
        assertThat(checkout.resolve(".git")).exists();

        when(codingTaskMapper.selectById("task-1")).thenReturn(task("WAITING_USER"));
        when(agentSessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(agentRuntimePort.start(any())).thenReturn(new AgentRuntimePort.StartResult(
                "s2", "FAILED", null, null, Map.of(), false, "NOT_AVAILABLE", "no codex"));
        // existing workspace row
        Workspace ws = new Workspace();
        ws.setId("ws1");
        ws.setTaskId("task-1");
        ws.setRootPath(temp.resolve("storage/workspaces/task-1").toString());
        when(workspaceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ws);

        var result = service.retry(user, "task-1", new TaskActionRequest("fix again"));
        assertThat(result.taskStatus()).isEqualTo("WAITING_USER");
        assertThat(result.sessionStatus()).isEqualTo("FAILED");
        assertThat(checkout.resolve(".git")).exists();
        verify(agentRuntimePort, never()).sendInput(anyString(), anyString());
    }

    @Test
    void successfulStartMovesTaskToRunningButDoesNotComplete() {
        when(agentRuntimePort.start(any())).thenReturn(new AgentRuntimePort.StartResult(
                "s1",
                "RUNNING",
                "http://127.0.0.1:19081/v1",
                "responses",
                Map.of("wire_api", "responses", "base_url", "http://127.0.0.1:19081/v1"),
                false,
                null,
                null));
        AuthUser user = new AuthUser("u1", "m", "成员", "t1", List.of("MEMBER"), List.of());
        var result = service.start(user, "task-1", new TaskActionRequest(null));
        assertThat(result.taskStatus()).isEqualTo("RUNNING");
        assertThat(result.sessionStatus()).isEqualTo("RUNNING");
        assertThat(result.taskStatus()).isNotEqualTo("COMPLETED");
    }

    private CodingTask task(String status) {
        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setProjectId("p1");
        task.setRequirementId("r1");
        task.setTitle("t");
        task.setStatus(status);
        task.setBranchName("fp/task-1");
        task.setCode("TASK-PAY-1");
        return task;
    }

    private Path initBare(LocalGitSupport git, Path bare) throws Exception {
        Path seed = temp.resolve("seed");
        Files.createDirectories(seed);
        Files.writeString(seed.resolve("a.txt"), "A");
        git.run(List.of("git", "init", "-b", "master"), seed, null);
        git.run(List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "add", "."), seed, null);
        git.run(List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-m", "init"), seed, null);
        git.run(List.of("git", "clone", "--bare", seed.toString(), bare.toString()), null, null);
        return bare;
    }
}
