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
import com.forgepilot.delivery.port.GitHostingPort.RepositoryRef;
import com.forgepilot.delivery.port.GitHostingPort.BranchView;
import com.forgepilot.delivery.port.GitHostingPort.DiffView;
import com.forgepilot.delivery.port.GitHostingPort.EnsureBranch;
import com.forgepilot.delivery.port.GitHostingPort.MergeRequestRef;
import com.forgepilot.delivery.port.GitHostingPort.RepositoryView;

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
import com.forgepilot.project.lifecycle.RequirementLifecycleService;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.outbox.InMemoryControlEventPublisher;
import com.forgepilot.system.outbox.OutboxService;
import com.forgepilot.delivery.git.InMemoryGitCredentialService;
import com.forgepilot.system.persistence.OutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.system.auth.AuthUser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeliveryPartialFailureTest {

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
    private Map<String, TaskRepositoryFreeze> freezes;

    @BeforeEach
    void setUp() throws Exception {
        git = new LocalGitSupport() {
            @Override
            public void push(Path checkout, String remote, String branchName, String askPassToken) {
                if (checkout != null && checkout.toString().contains("/web")) {
                    throw new IllegalStateException("simulated push failure for web");
                }
                super.push(checkout, remote, branchName, askPassToken);
            }
        };
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
                new InMemoryGitCredentialService(900),
                new OutboxService(mock(OutboxMapper.class), new ObjectMapper(), new InMemoryControlEventPublisher()),
                mock(RequirementLifecycleService.class),
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
        when(workspaceMapper.insert(any(Workspace.class))).thenReturn(1);
        when(workspaceCheckoutMapper.insert(any(WorkspaceCheckout.class))).thenReturn(1);
        when(mergeRequestMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(mergeRequestMapper.selectById(any())).thenReturn(null);
        when(mergeRequestMapper.insert(any(MergeRequestRecord.class))).thenReturn(1);
        when(gitHostingPort.supports(any())).thenReturn(true);

        freezes = new LinkedHashMap<>();
        freezes.put("repoA", freeze("repoA"));
        freezes.put("repoB", freeze("repoB"));
        when(freezeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenAnswer(inv -> new ArrayList<>(freezes.values()));
        AtomicInteger selectOneCalls = new AtomicInteger();
        when(freezeMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            // Approximate matching: after SHA writeback both have SHA; delivery visits A then B.
            int n = selectOneCalls.getAndIncrement();
            List<TaskRepositoryFreeze> ordered = new ArrayList<>(freezes.values());
            return ordered.get(n % ordered.size());
        });
        when(freezeMapper.updateById(any(TaskRepositoryFreeze.class))).thenAnswer(inv -> {
            TaskRepositoryFreeze f = inv.getArgument(0);
            freezes.put(f.getProjectRepositoryId(), f);
            return 1;
        });

        when(projectRepositoryMapper.selectById("repoA"))
                .thenReturn(repo("repoA", "api", remoteA.toUri().toString()));
        when(projectRepositoryMapper.selectById("repoB"))
                .thenReturn(repo("repoB", "web", remoteB.toUri().toString()));
    }

    @Test
    void partialPushFailureKeepsSuccessfulMrAndStaysWaitingUser() throws Exception {
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
                        repo -> null);
        Files.writeString(prepared.root().resolve("api/src/main/A.java"), "class A { int x; }");
        Files.writeString(prepared.root().resolve("web/src/test/BTest.java"), "class BTest { void t(){} }");
        freezes.put("repoA", freezeWithSha("repoA", prepared.checkouts().get(0).baseSha()));
        freezes.put("repoB", freezeWithSha("repoB", prepared.checkouts().get(1).baseSha()));

        var result = service.verify(user(), "task-1");
        assertThat(result.verificationPassed()).isTrue();
        assertThat(result.status()).isEqualTo("WAITING_USER");
        assertThat(result.mergeRequests()).hasSize(1);
        assertThat(result.mergeRequests().get(0).localDirectoryName()).isEqualTo("api");
        assertThat(result.message()).contains("retry-delivery");
        assertThat(freezes.get("repoA").getDeliveryState()).isEqualTo("MR_OPEN");
        assertThat(freezes.get("repoB").getDeliveryState()).isEqualTo("FAILED");
    }

    @Test
    void idempotentDraftMrPortContract() {
        AtomicInteger creates = new AtomicInteger();
        GitHostingPort port = new GitHostingPort() {
            MergeRequestView existing;

            @Override
            public boolean supports(String providerType) {
                return true;
            }

            @Override
            public RepositoryView getRepository(RepositoryRef ref) {
                return null;
            }

            @Override
            public java.util.List<BranchView> listBranches(RepositoryRef ref) {
                return List.of();
            }

            @Override
            public DiffView compare(RepositoryRef ref, String from, String to) {
                return null;
            }

            @Override
            public BranchView ensureBranch(EnsureBranch command) {
                return new BranchView(command.branchName(), command.startPointSha());
            }

            @Override
            public MergeRequestView createOrGetDraft(CreateDraft command) {
                if (existing == null) {
                    creates.incrementAndGet();
                    existing = new MergeRequestView(
                            command.repository().repositoryId(),
                            "7",
                            "https://gitlab.example/mr/7",
                            command.branchName(),
                            command.headSha(),
                            command.idempotencyKey(),
                            "OPEN");
                }
                return existing;
            }

            @Override
            public MergeRequestView getMergeRequest(MergeRequestRef ref) {
                return existing;
            }
        };

        RepositoryRef ref = new RepositoryRef("r1", "GITLAB", "1", "https://gitlab.example/a.git");
        CreateDraft cmd = new CreateDraft("t", ref, "fp/t", "sha", "main", "title", "t:r1");
        assertThat(port.createOrGetDraft(cmd).externalId()).isEqualTo("7");
        assertThat(port.createOrGetDraft(cmd).externalId()).isEqualTo("7");
        assertThat(creates.get()).isEqualTo(1);
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

    private TaskRepositoryFreeze freeze(String repoId) {
        return freezeWithSha(repoId, null);
    }

    private TaskRepositoryFreeze freezeWithSha(String repoId, String sha) {
        TaskRepositoryFreeze freeze = new TaskRepositoryFreeze();
        freeze.setId("f-" + repoId);
        freeze.setTaskId("task-1");
        freeze.setProjectRepositoryId(repoId);
        freeze.setBaseBranch("master");
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
