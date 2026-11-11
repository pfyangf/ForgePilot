package com.forgepilot.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.delivery.domain.MergeRequestRecord;
import com.forgepilot.delivery.domain.VerificationRun;
import com.forgepilot.delivery.git.GitCredentialPort;
import com.forgepilot.delivery.git.LocalGitSupport;
import com.forgepilot.delivery.git.MultiRepoWorkspaceCheckout;
import com.forgepilot.delivery.git.MultiRepoWorkspaceCheckout.FrozenRepo;
import com.forgepilot.delivery.git.MultiRepoWorkspaceCheckout.PreparedCheckout;
import com.forgepilot.delivery.git.MultiRepoWorkspaceCheckout.PreparedWorkspace;
import com.forgepilot.delivery.persistence.MergeRequestMapper;
import com.forgepilot.delivery.persistence.VerificationRunMapper;
import com.forgepilot.delivery.port.GitHostingPort;
import com.forgepilot.delivery.port.GitHostingPort.CreateDraft;
import com.forgepilot.delivery.port.GitHostingPort.MergeRequestView;
import com.forgepilot.delivery.port.GitHostingPort.RepositoryRef;
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
import com.forgepilot.project.persistence.ProjectRepositoryMapper;
import com.forgepilot.project.lifecycle.RequirementLifecycleService;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.outbox.OutboxService;
import com.forgepilot.system.auth.AuthUser;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 本机/开发用的验证与平台交付编排。不假装 Codex 已执行；只基于冻结仓库工作区跑项目命令并推送 Draft 合并请求。
 */
@Service
public class TaskVerifyDeliveryService {

    public static final String LOCAL_NODE_ID = "80000000-0000-4000-8000-000000000001";

    private final CodingTaskMapper codingTaskMapper;
    private final TaskRepositoryFreezeMapper freezeMapper;
    private final ProjectRepositoryMapper projectRepositoryMapper;
    private final ProjectMapper projectMapper;
    private final ProjectAccess projectAccess;
    private final MultiRepoWorkspaceCheckout workspaceCheckout;
    private final LocalGitSupport git;
    private final VerificationRunner verificationRunner;
    private final TestFileChangeDetector testFileChangeDetector;
    private final GitHostingPort gitHostingPort;
    private final MergeRequestMapper mergeRequestMapper;
    private final VerificationRunMapper verificationRunMapper;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceCheckoutMapper workspaceCheckoutMapper;
    private final TaskStateHistoryMapper historyMapper;
    private final ActivityLogRecorder activityLogRecorder;
    private final GitCredentialPort gitCredentialPort;
    private final OutboxService outboxService;
    private final RequirementLifecycleService lifecycleService;
    private final String storageRoot;

    public TaskVerifyDeliveryService(
            CodingTaskMapper codingTaskMapper,
            TaskRepositoryFreezeMapper freezeMapper,
            ProjectRepositoryMapper projectRepositoryMapper,
            ProjectMapper projectMapper,
            ProjectAccess projectAccess,
            MultiRepoWorkspaceCheckout workspaceCheckout,
            LocalGitSupport git,
            VerificationRunner verificationRunner,
            TestFileChangeDetector testFileChangeDetector,
            GitHostingPort gitHostingPort,
            MergeRequestMapper mergeRequestMapper,
            VerificationRunMapper verificationRunMapper,
            WorkspaceMapper workspaceMapper,
            WorkspaceCheckoutMapper workspaceCheckoutMapper,
            TaskStateHistoryMapper historyMapper,
            ActivityLogRecorder activityLogRecorder,
            GitCredentialPort gitCredentialPort,
            OutboxService outboxService,
            @Lazy RequirementLifecycleService lifecycleService,
            @Value("${forgepilot.workspace.storage-root:${java.io.tmpdir}/forgepilot-workspaces}") String storageRoot) {
        this.codingTaskMapper = codingTaskMapper;
        this.freezeMapper = freezeMapper;
        this.projectRepositoryMapper = projectRepositoryMapper;
        this.projectMapper = projectMapper;
        this.projectAccess = projectAccess;
        this.workspaceCheckout = workspaceCheckout;
        this.git = git;
        this.verificationRunner = verificationRunner;
        this.testFileChangeDetector = testFileChangeDetector;
        this.gitHostingPort = gitHostingPort;
        this.mergeRequestMapper = mergeRequestMapper;
        this.verificationRunMapper = verificationRunMapper;
        this.workspaceMapper = workspaceMapper;
        this.workspaceCheckoutMapper = workspaceCheckoutMapper;
        this.historyMapper = historyMapper;
        this.activityLogRecorder = activityLogRecorder;
        this.gitCredentialPort = gitCredentialPort;
        this.outboxService = outboxService;
        this.lifecycleService = lifecycleService;
        this.storageRoot = storageRoot;
    }

    @Transactional
    public VerifyDeliveryResult verify(AuthUser user, String taskId) {
        CodingTask task = requireTask(user, taskId);
        Project project = projectMapper.selectById(task.getProjectId());
        if (project == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在");
        }
        if ("COMPLETED".equals(task.getStatus())
                || "CANCELLED".equals(task.getStatus())
                || "FAILED".equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "终态任务不能验证");
        }

        List<FrozenRepo> frozen = loadFrozen(taskId);
        if (frozen.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务没有冻结仓库集合");
        }
        String from = task.getStatus();
        task.setStatus("VERIFYING");
        codingTaskMapper.updateById(task);
        lifecycleService.syncAutomaticStatus(task.getRequirementId());
        recordHistory(taskId, from, "VERIFYING", "开始平台验证", user.userId());

        PreparedWorkspace workspace = workspaceCheckout.prepare(storageRoot, taskId, frozen, this::askPassFor);
        Workspace ws = persistWorkspace(user, task, workspace);

        // 把冻结 baseSha 回写（首次检出）
        for (PreparedCheckout checkout : workspace.checkouts()) {
            TaskRepositoryFreeze freeze = freezeMapper.selectOne(new LambdaQueryWrapper<TaskRepositoryFreeze>()
                    .eq(TaskRepositoryFreeze::getTaskId, taskId)
                    .eq(TaskRepositoryFreeze::getProjectRepositoryId, checkout.projectRepositoryId()));
            if (freeze != null && (freeze.getBaseSha() == null || freeze.getBaseSha().isBlank())) {
                freeze.setBaseSha(checkout.baseSha());
                freezeMapper.updateById(freeze);
            }
        }

        List<String> commands = new ArrayList<>();
        if (project.getBuildCommand() != null && !project.getBuildCommand().isBlank()) {
            commands.add(project.getBuildCommand());
        }
        if (project.getTestCommand() != null && !project.getTestCommand().isBlank()) {
            commands.add(project.getTestCommand());
        }
        if (commands.isEmpty()) {
            commands.add("true");
        }
        int timeoutSec = project.getVerificationTimeoutSeconds() == null ? 600 : project.getVerificationTimeoutSeconds();
        int attempt = nextAttempt(taskId);
        int feedbackLimit = project.getFeedbackLimit() == null ? 2 : project.getFeedbackLimit();

        VerificationRunner.VerificationResult result =
                verificationRunner.run(workspace.root(), commands, Duration.ofSeconds(timeoutSec));

        VerificationRun run = new VerificationRun();
        run.setId(UUID.randomUUID().toString());
        run.setTaskId(taskId);
        run.setWorkspaceId(ws.getId());
        run.setCommand(String.join(" && ", commands));
        run.setTimeoutSeconds(timeoutSec);
        run.setExitCode(result.exitCode());
        run.setAttempt(attempt);
        run.setCreatedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        verificationRunMapper.insert(run);

        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "TASK_VERIFY",
                "TASK",
                taskId,
                task.getProjectId(),
                task.getRequirementId(),
                taskId,
                null,
                result.passed() ? "验证通过" : "验证未通过",
                Map.of(
                        "attempt",
                        attempt,
                        "feedbackLimit",
                        feedbackLimit,
                        "exitCode",
                        result.exitCode(),
                        "passed",
                        result.passed(),
                        "outputExcerpt",
                        excerpt(result.output())),
                result.passed() ? "SUCCESS" : "FAILURE");

        if (!result.passed()) {
            String to = "WAITING_USER";
            task.setStatus(to);
            codingTaskMapper.updateById(task);
            lifecycleService.syncAutomaticStatus(task.getRequirementId());
            String reason = attempt >= feedbackLimit
                    ? "验证回灌次数已用尽，进入 WAITING_USER（非 FAILED）"
                    : "验证未通过，可再次调用 verify 重试（尚未接 Codex 回灌）";
            recordHistory(taskId, "VERIFYING", to, reason, user.userId());
            return new VerifyDeliveryResult(
                    taskId,
                    to,
                    false,
                    result.exitCode(),
                    attempt,
                    attempt >= feedbackLimit,
                    List.of(),
                    List.of(),
                    reason,
                    workspace.root().toString());
        }

        DeliveryOutcome delivery = deliverChangedRepos(user, task, workspace);
        return new VerifyDeliveryResult(
                taskId,
                delivery.taskStatus(),
                true,
                result.exitCode(),
                attempt,
                false,
                delivery.mergeRequests(),
                delivery.testFileChanges(),
                delivery.message(),
                workspace.root().toString());
    }

    @Transactional
    public VerifyDeliveryResult retryDelivery(AuthUser user, String taskId) {
        CodingTask task = requireTask(user, taskId);
        if (!"WAITING_USER".equals(task.getStatus()) && !"VERIFYING".equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不能重试交付");
        }
        List<FrozenRepo> frozen = loadFrozen(taskId);
        PreparedWorkspace workspace = workspaceCheckout.prepare(storageRoot, taskId, frozen, this::askPassFor);
        persistWorkspace(user, task, workspace);
        DeliveryOutcome delivery = deliverChangedRepos(user, task, workspace);
        return new VerifyDeliveryResult(
                taskId,
                delivery.taskStatus(),
                true,
                0,
                lastAttempt(taskId),
                false,
                delivery.mergeRequests(),
                delivery.testFileChanges(),
                delivery.message(),
                workspace.root().toString());
    }

    private DeliveryOutcome deliverChangedRepos(AuthUser user, CodingTask task, PreparedWorkspace workspace) {
        List<MrSummary> mrs = new ArrayList<>();
        List<String> testChanges = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int needed = 0;

        for (PreparedCheckout checkout : workspace.checkouts()) {
            String baseSha = checkout.baseSha();
            TaskRepositoryFreeze freeze = freezeMapper.selectOne(new LambdaQueryWrapper<TaskRepositoryFreeze>()
                    .eq(TaskRepositoryFreeze::getTaskId, task.getId())
                    .eq(TaskRepositoryFreeze::getProjectRepositoryId, checkout.projectRepositoryId()));
            if (freeze != null && freeze.getBaseSha() != null && !freeze.getBaseSha().isBlank()) {
                baseSha = freeze.getBaseSha();
            }
            String branch = workingBranch(task, freeze);

            // 若工作区有未提交改动，先提交到平台分支（本机开发路径；真实 Agent 会话会自己改文件）
            git.checkoutBranch(checkout.path(), branch);
            git.commitAllIfDirty(checkout.path(), "forgepilot: local changes for " + task.getId());

            boolean changed = git.hasChangesSince(checkout.path(), baseSha);
            List<String> changedFiles = git.changedFilesSince(checkout.path(), baseSha);
            List<String> testHits = testFileChangeDetector.findTestFileChanges(changedFiles);
            for (String hit : testHits) {
                testChanges.add(checkout.localDirectoryName() + ": " + hit);
            }

            if (!changed) {
                if (freeze != null) {
                    freeze.setDeliveryState("NONE");
                    freezeMapper.updateById(freeze);
                }
                continue;
            }
            needed++;

            // 已有成功 MR 则跳过（重试只补未完成）
            if (freeze != null && "MR_OPEN".equals(freeze.getDeliveryState())) {
                MergeRequestRecord existing = findOpenMr(task.getId(), checkout.projectRepositoryId());
                if (existing != null) {
                    mrs.add(new MrSummary(
                            checkout.projectRepositoryId(),
                            checkout.localDirectoryName(),
                            existing.getUrl(),
                            existing.getExternalId(),
                            existing.getHeadSha(),
                            true));
                    continue;
                }
            }

            try {
                String headSha = git.revParse(checkout.path(), "HEAD");
                gitCredentialPort.requireAskPassTokenForPush(checkout.cloneUrl());
                git.push(checkout.path(), "origin", branch, askPassFor(checkout.cloneUrl()));

                String idempotencyKey = task.getId() + ":" + checkout.projectRepositoryId();
                MergeRequestView view;
                if (checkout.cloneUrl() != null && checkout.cloneUrl().startsWith("file:")) {
                    // 本机 file:// 远端：不调用真实 GitLab，签发合成 Draft 合并请求，便于无远程托管时验收交付编排。
                    view = new MergeRequestView(
                            checkout.projectRepositoryId(),
                            "local-" + checkout.localDirectoryName(),
                            "file://local-mr/" + task.getId() + "/" + checkout.localDirectoryName(),
                            branch,
                            headSha,
                            idempotencyKey,
                            "OPEN");
                } else {
                    if (!gitHostingPort.supports(checkout.providerType())) {
                        throw new IllegalStateException("不支持的托管类型: " + checkout.providerType());
                    }
                    RepositoryRef ref = new RepositoryRef(
                            checkout.projectRepositoryId(),
                            checkout.providerType(),
                            checkout.externalId(),
                            checkout.cloneUrl());
                    view = gitHostingPort.createOrGetDraft(new CreateDraft(
                            task.getId(),
                            ref,
                            branch,
                            headSha,
                            checkout.baseBranch(),
                            "Draft: " + task.getTitle(),
                            idempotencyKey));
                }

                MergeRequestRecord record = findOpenMr(task.getId(), checkout.projectRepositoryId());
                if (record == null) {
                    record = new MergeRequestRecord();
                    record.setId(UUID.randomUUID().toString());
                    record.setTaskId(task.getId());
                    record.setProjectRepositoryId(checkout.projectRepositoryId());
                    record.setProviderType(checkout.providerType());
                    record.setIdempotencyKey(idempotencyKey);
                    record.setCreatedBy(user.userId());
                }
                record.setExternalId(view.externalId());
                record.setUrl(view.url());
                record.setBranchName(branch);
                record.setHeadSha(headSha);
                record.setState("OPEN");
                if (record.getId() != null && mergeRequestMapper.selectById(record.getId()) != null) {
                    mergeRequestMapper.updateById(record);
                } else {
                    // 可能是新对象但 idempotency 已存在
                    MergeRequestRecord byKey = mergeRequestMapper.selectOne(new LambdaQueryWrapper<MergeRequestRecord>()
                            .eq(MergeRequestRecord::getIdempotencyKey, idempotencyKey));
                    if (byKey != null) {
                        byKey.setExternalId(view.externalId());
                        byKey.setUrl(view.url());
                        byKey.setHeadSha(headSha);
                        byKey.setState("OPEN");
                        mergeRequestMapper.updateById(byKey);
                        record = byKey;
                    } else {
                        mergeRequestMapper.insert(record);
                    }
                }

                if (freeze != null) {
                    freeze.setDeliveryState("MR_OPEN");
                    freezeMapper.updateById(freeze);
                }
                mrs.add(new MrSummary(
                        checkout.projectRepositoryId(),
                        checkout.localDirectoryName(),
                        record.getUrl(),
                        record.getExternalId(),
                        record.getHeadSha(),
                        false));
            } catch (RuntimeException ex) {
                failures.add(checkout.localDirectoryName() + ": " + ex.getMessage());
                if (freeze != null) {
                    freeze.setDeliveryState("FAILED");
                    freezeMapper.updateById(freeze);
                }
                activityLogRecorder.record(
                        user.tenantId(),
                        "USER",
                        user.userId(),
                        "TASK_DELIVERY_PARTIAL_FAILURE",
                        "TASK",
                        task.getId(),
                        task.getProjectId(),
                        task.getRequirementId(),
                        task.getId(),
                        null,
                        "仓库交付失败：" + checkout.localDirectoryName(),
                        Map.of(
                                "projectRepositoryId",
                                checkout.projectRepositoryId(),
                                "error",
                                ex.getMessage() == null ? "" : ex.getMessage()),
                        "FAILURE");
            }
        }

        String toStatus;
        String message;
        if (needed == 0) {
            // 无改动也进入待审核（全部 MR 齐了：需要 0 条）
            toStatus = "WAITING_APPROVAL";
            message = "验证通过且无改动仓库，无需 Draft 合并请求，进入待审核";
        } else if (failures.isEmpty() && mrs.size() >= needed) {
            toStatus = "WAITING_APPROVAL";
            message = "验证通过，全部需要的 Draft 合并请求已创建或命中";
        } else if (!failures.isEmpty()) {
            toStatus = "WAITING_USER";
            message = "部分仓库推送或创建 Draft 合并请求失败，已成功的请求保留，可 retry-delivery：" + String.join("; ", failures);
        } else {
            toStatus = "WAITING_USER";
            message = "交付未完成";
        }

        String from = task.getStatus();
        task.setStatus(toStatus);
        codingTaskMapper.updateById(task);
        lifecycleService.syncAutomaticStatus(task.getRequirementId());
        recordHistory(task.getId(), from, toStatus, message, user.userId());
        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "TASK_DELIVERY",
                "TASK",
                task.getId(),
                task.getProjectId(),
                task.getRequirementId(),
                task.getId(),
                null,
                message,
                Map.of(
                        "needed",
                        needed,
                        "mrCount",
                        mrs.size(),
                        "failures",
                        failures,
                        "testFileChanges",
                        testChanges,
                        "branchName",
                        task.getBranchName()),
                failures.isEmpty() ? "SUCCESS" : "FAILURE");
        outboxService.enqueue(
                "DELIVERY_RESULT",
                "TASK",
                task.getId(),
                null,
                Map.of(
                        "taskId", task.getId(),
                        "status", toStatus,
                        "needed", needed,
                        "mrCount", mrs.size(),
                        "failureCount", failures.size()));
        return new DeliveryOutcome(toStatus, message, mrs, testChanges);
    }


    private String askPassFor(FrozenRepo repo) {
        return askPassFor(repo == null ? null : repo.cloneUrl());
    }

    private String askPassFor(String cloneUrl) {
        return gitCredentialPort.askPassTokenFor(cloneUrl);
    }

    private List<FrozenRepo> loadFrozen(String taskId) {
        List<TaskRepositoryFreeze> freezes = freezeMapper.selectList(new LambdaQueryWrapper<TaskRepositoryFreeze>()
                .eq(TaskRepositoryFreeze::getTaskId, taskId));
        List<FrozenRepo> result = new ArrayList<>();
        for (TaskRepositoryFreeze freeze : freezes) {
            ProjectRepository repo = projectRepositoryMapper.selectById(freeze.getProjectRepositoryId());
            if (repo == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "冻结仓库连接已缺失: " + freeze.getProjectRepositoryId());
            }
            result.add(new FrozenRepo(
                    repo.getId(),
                    repo.getLocalDirectoryName(),
                    repo.getCloneUrl(),
                    freeze.getBaseBranch(),
                    freeze.getBaseSha(),
                    repo.getProviderType(),
                    repo.getExternalId()));
        }
        return result;
    }

    private Workspace persistWorkspace(AuthUser user, CodingTask task, PreparedWorkspace prepared) {
        Workspace existing = workspaceMapper.selectOne(new LambdaQueryWrapper<Workspace>().eq(Workspace::getTaskId, task.getId()));
        if (existing != null) {
            return existing;
        }
        Workspace ws = new Workspace();
        ws.setId(UUID.randomUUID().toString());
        ws.setTaskId(task.getId());
        ws.setNodeId(LOCAL_NODE_ID);
        ws.setRootPath(prepared.root().toString());
        ws.setAgentHomePath(prepared.root().resolve(".agent-home").toString());
        ws.setStatus("READY");
        ws.setCreatedBy(user.userId());
        workspaceMapper.insert(ws);
        for (PreparedCheckout checkout : prepared.checkouts()) {
            WorkspaceCheckout row = new WorkspaceCheckout();
            row.setId(UUID.randomUUID().toString());
            row.setWorkspaceId(ws.getId());
            row.setProjectRepositoryId(checkout.projectRepositoryId());
            row.setLocalDirectoryName(checkout.localDirectoryName());
            row.setClonePath(checkout.path().toString());
            row.setBaseSha(checkout.baseSha());
            row.setBranchName(task.getBranchName());
            row.setStatus("READY");
            workspaceCheckoutMapper.insert(row);
        }
        return ws;
    }

    private MergeRequestRecord findOpenMr(String taskId, String projectRepositoryId) {
        return mergeRequestMapper.selectOne(new LambdaQueryWrapper<MergeRequestRecord>()
                .eq(MergeRequestRecord::getTaskId, taskId)
                .eq(MergeRequestRecord::getProjectRepositoryId, projectRepositoryId)
                .eq(MergeRequestRecord::getState, "OPEN"));
    }

    private int nextAttempt(String taskId) {
        Long count = verificationRunMapper.selectCount(new LambdaQueryWrapper<VerificationRun>().eq(VerificationRun::getTaskId, taskId));
        return (count == null ? 0 : count.intValue()) + 1;
    }

    private int lastAttempt(String taskId) {
        Long count = verificationRunMapper.selectCount(new LambdaQueryWrapper<VerificationRun>().eq(VerificationRun::getTaskId, taskId));
        return count == null ? 0 : count.intValue();
    }

    private CodingTask requireTask(AuthUser user, String taskId) {
        CodingTask task = codingTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在或无权查看");
        }
        projectAccess.requireProjectMember(user, task.getProjectId());
        return task;
    }

    private void recordHistory(String taskId, String from, String to, String reason, String actorId) {
        TaskStateHistory history = new TaskStateHistory();
        history.setId(UUID.randomUUID().toString());
        history.setTaskId(taskId);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setReason(reason);
        history.setActorId(actorId);
        history.setCreatedAt(Instant.now());
        historyMapper.insert(history);
    }

    private static String excerpt(String output) {
        if (output == null) {
            return "";
        }
        return output.length() <= 2000 ? output : output.substring(0, 2000);
    }


    public static String workingBranch(CodingTask task, TaskRepositoryFreeze freeze) {
        if (freeze != null && freeze.getWorkingBranch() != null && !freeze.getWorkingBranch().isBlank()) {
            return freeze.getWorkingBranch();
        }
        if (task.getBranchName() != null && !task.getBranchName().isBlank()) {
            return task.getBranchName();
        }
        return "fp/" + task.getId();
    }

    private record DeliveryOutcome(
            String taskStatus, String message, List<MrSummary> mergeRequests, List<String> testFileChanges) {
    }

    public record MrSummary(
            String projectRepositoryId,
            String localDirectoryName,
            String url,
            String externalId,
            String headSha,
            boolean reused) {
    }

    public record VerifyDeliveryResult(
            String taskId,
            String status,
            boolean verificationPassed,
            int exitCode,
            int attempt,
            boolean verifyRetriesExhausted,
            List<MrSummary> mergeRequests,
            List<String> testFileChanges,
            String message,
            String workspaceRoot) {
    }
}
