package com.forgepilot.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.delivery.domain.MergeRequestRecord;
import com.forgepilot.delivery.domain.VerificationRun;
import com.forgepilot.delivery.git.LocalGitSupport;
import com.forgepilot.delivery.git.MultiRepoWorkspaceCheckout;
import com.forgepilot.delivery.persistence.MergeRequestMapper;
import com.forgepilot.delivery.persistence.VerificationRunMapper;
import com.forgepilot.delivery.port.GitHostingPort;
import com.forgepilot.delivery.port.GitHostingPort.CreateDraft;
import com.forgepilot.delivery.port.GitHostingPort.MergeRequestView;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.domain.TaskRepositoryFreeze;
import com.forgepilot.execution.domain.TaskStateHistory;
import com.forgepilot.execution.domain.Workspace;
import com.forgepilot.execution.domain.WorkspaceCheckout;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.execution.persistence.TaskRepositoryFreezeMapper;
import com.forgepilot.execution.persistence.TaskStateHistoryMapper;
import com.forgepilot.execution.persistence.WorkspaceCheckoutMapper;
import com.forgepilot.execution.persistence.WorkspaceMapper;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.ProjectRepository;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.project.persistence.ProjectRepositoryMapper;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class VerificationAndDeliveryTest {

    @TempDir
    Path temp;

    private final CodingTaskMapper codingTaskMapper = mock(CodingTaskMapper.class);
    private final TaskRepositoryFreezeMapper freezeMapper = mock(TaskRepositoryFreezeMapper.class);
    private final ProjectRepositoryMapper projectRepositoryMapper = mock(ProjectRepositoryMapper.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final ProjectMemberMapper projectMemberMapper = mock(ProjectMemberMapper.class);
    private final MergeRequestMapper mergeRequestMapper = mock(MergeRequestMapper.class);
    private final VerificationRunMapper verificationRunMapper = mock(VerificationRunMapper.class);
    private final WorkspaceMapper workspaceMapper = mock(WorkspaceMapper.class);
    private final WorkspaceCheckoutMapper workspaceCheckoutMapper = mock(WorkspaceCheckoutMapper.class);
    private final TaskStateHistoryMapper historyMapper = mock(TaskStateHistoryMapper.class);
    private final ActivityLogRecorder recorder = mock(ActivityLogRecorder.class);
    private final GitHostingPort gitHostingPort = mock(GitHostingPort.class);

    private LocalGitSupport git;
    private TaskVerifyDeliveryService service;
    private Path remoteA;
    private Path remoteB;
    private java.util.Map<String, TaskRepositoryFreeze> freezes;
    private final AtomicInteger freezeSelectOneCalls = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        git = new LocalGitSupport();
        remoteA = initBare(temp.resolve("a.git"), "src/main/A.java", "class A {}");
        remoteB = initBare(temp.resolve("b.git"), "src/test/BTest.java", "class BTest {}");
        when(projectMemberMapper.findProjectIdsByUserId("u1")).thenReturn(List.of("p1"));
        ProjectAccess access = new ProjectAccess(projectMapper, projectMemberMapper);
        service = new TaskVerifyDeliveryService(
                codingTaskMapper,
                freezeMapper,
                projectRepositoryMapper,
                projectMapper,
                access,
                new MultiRepoWorkspaceCheckout(git),
                git,
                new LocalProcessVerificationRunner(),
                new TestFileChangeDetector(),
                gitHostingPort,
                mergeRequestMapper,
                verificationRunMapper,
                workspaceMapper,
                workspaceCheckoutMapper,
                historyMapper,
                recorder,
                temp.resolve("storage").toString());

        Project project = new Project();
        project.setId("p1");
        project.setTenantId("t1");
        project.setName("支付");
        project.setCode("pay");
        project.setStatus("ACTIVE");
        project.setFeedbackLimit(2);
        project.setBuildCommand("true");
        project.setTestCommand("true");
        project.setVerificationTimeoutSeconds(30);
        when(projectMapper.selectById("p1")).thenReturn(project);

        CodingTask task = task("PENDING");
        when(codingTaskMapper.selectById("task-1")).thenReturn(task);
        when(codingTaskMapper.updateById(any(CodingTask.class))).thenReturn(1);
        when(historyMapper.insert(any(TaskStateHistory.class))).thenReturn(1);
        when(verificationRunMapper.insert(any(VerificationRun.class))).thenReturn(1);
        when(verificationRunMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(workspaceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(workspaceMapper.insert(any(Workspace.class))).thenAnswer(inv -> {
            Workspace ws = inv.getArgument(0);
            return 1;
        });
        when(workspaceCheckoutMapper.insert(any(WorkspaceCheckout.class))).thenReturn(1);
        when(mergeRequestMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(mergeRequestMapper.selectById(any())).thenReturn(null);
        when(mergeRequestMapper.insert(any(MergeRequestRecord.class))).thenReturn(1);
        when(gitHostingPort.supports(any())).thenReturn(true);

        freezes = new java.util.LinkedHashMap<>();
        freezes.put("repoA", freeze("repoA", "master"));
        freezes.put("repoB", freeze("repoB", "master"));
        freezeSelectOneCalls.set(0);
        when(freezeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenAnswer(inv -> new java.util.ArrayList<>(freezes.values()));
        when(freezeMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            java.util.List<TaskRepositoryFreeze> ordered = new java.util.ArrayList<>(freezes.values());
            return ordered.get(freezeSelectOneCalls.getAndIncrement() % ordered.size());
        });
        when(freezeMapper.updateById(any(TaskRepositoryFreeze.class))).thenAnswer(inv -> {
            TaskRepositoryFreeze f = inv.getArgument(0);
            freezes.put(f.getProjectRepositoryId(), f);
            return 1;
        });

        ProjectRepository r1 = repo("repoA", "api", remoteA.toUri().toString());
        ProjectRepository r2 = repo("repoB", "web", remoteB.toUri().toString());
        when(projectRepositoryMapper.selectById("repoA")).thenReturn(r1);
        when(projectRepositoryMapper.selectById("repoB")).thenReturn(r2);
    }

    @Test
    void verifyFromWorkspaceRootThenWaitingApprovalWhenAllMrsExist() throws Exception {
        AtomicInteger mrCalls = new AtomicInteger();
        when(gitHostingPort.createOrGetDraft(any(CreateDraft.class))).thenAnswer(inv -> {
            CreateDraft cmd = inv.getArgument(0);
            mrCalls.incrementAndGet();
            return new MergeRequestView(
                    cmd.repository().repositoryId(),
                    "1",
                    "https://gitlab.example/mr/1",
                    cmd.branchName(),
                    cmd.headSha(),
                    cmd.idempotencyKey(),
                    "OPEN");
        });

        // 先 prepare 一次以拿到工作区，再改文件
        AuthUser user = user();
        // 手动准备并修改一个仓库，模拟 Agent 产出
        var prepared = new MultiRepoWorkspaceCheckout(git)
                .prepare(
                        temp.resolve("storage").toString(),
                        "task-1",
                        List.of(
                                new MultiRepoWorkspaceCheckout.FrozenRepo(
                                        "repoA",
                                        "api",
                                        remoteA.toUri().toString(),
                                        "master",
                                        null,
                                        "GITLAB",
                                        null),
                                new MultiRepoWorkspaceCheckout.FrozenRepo(
                                        "repoB",
                                        "web",
                                        remoteB.toUri().toString(),
                                        "master",
                                        null,
                                        "GITLAB",
                                        null)),
                        null);
        Files.writeString(prepared.root().resolve("api/src/main/A.java"), "class A { int x; }");
        Files.writeString(prepared.root().resolve("web/src/test/BTest.java"), "class BTest { void t(){} }");

        freezes.clear();
        freezes.put("repoA", freezeWithSha("repoA", "master", prepared.checkouts().get(0).baseSha()));
        freezes.put("repoB", freezeWithSha("repoB", "master", prepared.checkouts().get(1).baseSha()));
        freezeSelectOneCalls.set(0);

        var result = service.verify(user, "task-1");
        assertThat(result.verificationPassed()).isTrue();
        assertThat(result.status()).isEqualTo("WAITING_APPROVAL");
        assertThat(result.mergeRequests()).hasSize(2);
        assertThat(result.testFileChanges()).isNotEmpty();
        assertThat(result.workspaceRoot()).contains("workspaces");
    }

    @Test
    void verifyRetryExhaustionGoesToWaitingUserNotFailed() {
        Project project = projectMapper.selectById("p1");
        project.setBuildCommand("false");
        project.setTestCommand(null);
        project.setFeedbackLimit(1);
        when(verificationRunMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 1L);

        AuthUser user = user();
        var first = service.verify(user, "task-1");
        assertThat(first.verificationPassed()).isFalse();
        assertThat(first.status()).isEqualTo("WAITING_USER");

        when(codingTaskMapper.selectById("task-1")).thenReturn(task("WAITING_USER"));
        var second = service.verify(user, "task-1");
        assertThat(second.status()).isEqualTo("WAITING_USER");
        assertThat(second.verifyRetriesExhausted()).isTrue();
        ArgumentCaptor<CodingTask> captor = ArgumentCaptor.forClass(CodingTask.class);
        Mockito.verify(codingTaskMapper, Mockito.atLeastOnce()).updateById(captor.capture());
        assertThat(captor.getAllValues().stream().map(CodingTask::getStatus)).doesNotContain("FAILED");
    }


    private AuthUser user() {
        return new AuthUser("u1", "m", "成员", "t1", List.of("MEMBER"), List.of());
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

    private TaskRepositoryFreeze freeze(String repoId, String branch) {
        return freezeWithSha(repoId, branch, null);
    }

    private TaskRepositoryFreeze freezeWithSha(String repoId, String branch, String sha) {
        TaskRepositoryFreeze freeze = new TaskRepositoryFreeze();
        freeze.setId("f-" + repoId);
        freeze.setTaskId("task-1");
        freeze.setProjectRepositoryId(repoId);
        freeze.setBaseBranch(branch);
        freeze.setBaseSha(sha);
        freeze.setDeliveryState("NONE");
        return freeze;
    }

    private ProjectRepository repo(String id, String dir, String url) {
        ProjectRepository repo = new ProjectRepository();
        repo.setId(id);
        repo.setProjectId("p1");
        repo.setLocalDirectoryName(dir);
        repo.setCloneUrl(url);
        repo.setDefaultBranch("master");
        repo.setProviderType("GITLAB");
        repo.setDisplayName(dir);
        return repo;
    }

    private Path initBare(Path bare, String file, String content) throws Exception {
        Path seed = temp.resolve(bare.getFileName().toString() + "-seed");
        Files.createDirectories(seed);
        Path parent = seed;
        Path target = seed.resolve(file);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
        git.run(List.of("git", "init", "-b", "master"), seed, null);
        git.run(List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "add", "."), seed, null);
        git.run(List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-m", "init"), seed, null);
        git.run(List.of("git", "clone", "--bare", seed.toString(), bare.toString()), null, null);
        return bare;
    }
}
