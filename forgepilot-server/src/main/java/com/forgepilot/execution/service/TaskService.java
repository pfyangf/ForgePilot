package com.forgepilot.execution.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.domain.TaskRepositoryFreeze;
import com.forgepilot.execution.domain.TaskStateHistory;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.execution.persistence.TaskRepositoryFreezeMapper;
import com.forgepilot.execution.persistence.TaskStateHistoryMapper;
import com.forgepilot.delivery.domain.MergeRequestRecord;
import com.forgepilot.delivery.persistence.MergeRequestMapper;
import com.forgepilot.execution.branch.WorkingBranchNames;
import com.forgepilot.execution.web.CreateTaskRequest;
import com.forgepilot.execution.web.RepoCheckoutBinding;
import com.forgepilot.execution.web.TaskActionRequest;
import com.forgepilot.execution.web.UpdateTaskRequest;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.ProjectRepository;
import com.forgepilot.project.domain.Requirement;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectRepositoryMapper;
import com.forgepilot.project.persistence.RequirementMapper;
import com.forgepilot.project.lifecycle.RequirementLifecycleService;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 任务生命周期（创建与人工状态迁移）。不创建 Agent Session，也不触发 Codex。
 */
@Service
public class TaskService {

    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED", "CANCELLED");

    private final CodingTaskMapper codingTaskMapper;
    private final TaskRepositoryFreezeMapper taskRepositoryFreezeMapper;
    private final TaskStateHistoryMapper taskStateHistoryMapper;
    private final ProjectRepositoryMapper projectRepositoryMapper;
    private final RequirementMapper requirementMapper;
    private final ProjectMapper projectMapper;
    private final ProjectAccess projectAccess;
    private final ActivityLogRecorder activityLogRecorder;
    private final MergeRequestMapper mergeRequestMapper;
    private final RequirementLifecycleService lifecycleService;

    public TaskService(
            CodingTaskMapper codingTaskMapper,
            TaskRepositoryFreezeMapper taskRepositoryFreezeMapper,
            TaskStateHistoryMapper taskStateHistoryMapper,
            ProjectRepositoryMapper projectRepositoryMapper,
            RequirementMapper requirementMapper,
            ProjectMapper projectMapper,
            ProjectAccess projectAccess,
            ActivityLogRecorder activityLogRecorder,
            MergeRequestMapper mergeRequestMapper,
            @Lazy RequirementLifecycleService lifecycleService) {
        this.codingTaskMapper = codingTaskMapper;
        this.taskRepositoryFreezeMapper = taskRepositoryFreezeMapper;
        this.taskStateHistoryMapper = taskStateHistoryMapper;
        this.projectRepositoryMapper = projectRepositoryMapper;
        this.requirementMapper = requirementMapper;
        this.projectMapper = projectMapper;
        this.projectAccess = projectAccess;
        this.activityLogRecorder = activityLogRecorder;
        this.mergeRequestMapper = mergeRequestMapper;
        this.lifecycleService = lifecycleService;
    }

    public List<TaskView> listByRequirement(AuthUser user, String requirementId) {
        Requirement requirement = requireRequirement(user, requirementId);
        Project project = projectMapper.selectById(requirement.getProjectId());
        return codingTaskMapper
                .selectList(new LambdaQueryWrapper<CodingTask>()
                        .eq(CodingTask::getRequirementId, requirementId)
                        .orderByAsc(CodingTask::getCode))
                .stream()
                .map(task -> toView(task, project == null ? "" : project.getName(), frozenRepos(task.getId())))
                .toList();
    }

    public TaskDetailView get(AuthUser user, String taskId) {
        CodingTask task = requireTask(user, taskId);
        Project project = projectMapper.selectById(task.getProjectId());
        List<FrozenRepoView> frozen = frozenRepos(taskId);
        List<MergeRequestView> mrs = openMergeRequests(taskId, frozen);
        return new TaskDetailView(
                toView(task, project == null ? "" : project.getName(), frozen),
                frozen,
                mrs,
                List.of(),
                "审核页可用 verify/delivery 活动日志中的 testFileChanges，或后续对 diff 调用 TestFileChangeDetector",
                unsupportedActions(task));
    }

    @Transactional
    public TaskView create(AuthUser user, String requirementId, CreateTaskRequest request) {
        Requirement requirement = requireRequirement(user, requirementId);
        Project project = projectAccess.requireProjectMember(user, requirement.getProjectId());
        if ("ARCHIVED".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "归档项目不能创建任务");
        }
        if ("已取消".equals(requirement.getStatus())
                || "已完成".equals(requirement.getStatus())
                || "已发布".equals(requirement.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已完成、已取消或已发布的需求不能再拆任务");
        }
        if ("BUGFIX".equals(request.taskType())) {
            lifecycleService.requireDefectForBugfix(requirementId, request.defectId());
        }

        List<ProjectRepository> repos = projectRepositoryMapper.selectList(new LambdaQueryWrapper<ProjectRepository>()
                .eq(ProjectRepository::getProjectId, project.getId())
                .orderByAsc(ProjectRepository::getLocalDirectoryName));
        if (repos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目尚未关联仓库，无法创建任务并冻结仓库集合");
        }

        if (request.predecessorTaskId() != null && !request.predecessorTaskId().isBlank()) {
            CodingTask predecessor = codingTaskMapper.selectById(request.predecessorTaskId());
            if (predecessor == null || !requirementId.equals(predecessor.getRequirementId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "前置任务必须属于同一需求");
            }
        }

        String taskId = UUID.randomUUID().toString();
        String taskCode = nextTaskCode(project.getCode());
        java.util.LinkedHashSet<String> taken = takenWorkingBranches(project.getId(), null);
        String workingBranch = resolveWorkingBranch(request.workingBranch(), requirement.getCode(), taskCode, taken);
        CodingTask task = new CodingTask();
        task.setId(taskId);
        task.setProjectId(project.getId());
        task.setRequirementId(requirementId);
        task.setCode(taskCode);
        task.setTitle(request.title().trim());
        task.setDescription(blankToNull(request.description()));
        task.setTaskType(request.taskType());
        task.setPriority(request.priority() == null || request.priority().isBlank() ? "MEDIUM" : request.priority());
        task.setBranchName(workingBranch);
        task.setStatus("PENDING");
        task.setOwnerId(request.ownerId() == null || request.ownerId().isBlank() ? user.userId() : request.ownerId());
        task.setPredecessorTaskId(blankToNull(request.predecessorTaskId()));
        task.setDefectId("BUGFIX".equals(request.taskType()) ? blankToNull(request.defectId()) : null);
        task.setModelProviderId(blankToNull(request.modelProviderId()));
        task.setModelId(blankToNull(request.modelId()));
        task.setAgentProfileId(blankToNull(request.agentProfileId()));
        task.setCreatedBy(user.userId());
        codingTaskMapper.insert(task);

        Instant frozenAt = Instant.now();
        List<ProjectRepository> selected = selectRepositories(repos, request.repositories());
        for (ProjectRepository repo : selected) {
            RepoCheckoutBinding binding = bindingFor(request.repositories(), repo.getId());
            String source = resolveSourceBranch(binding, repo);
            String repoWorking = binding != null && notBlank(binding.workingBranch())
                    ? sanitizeBranch(binding.workingBranch())
                    : workingBranch;
            TaskRepositoryFreeze freeze = new TaskRepositoryFreeze();
            freeze.setId(UUID.randomUUID().toString());
            freeze.setTaskId(taskId);
            freeze.setProjectRepositoryId(repo.getId());
            freeze.setBaseBranch(source);
            freeze.setWorkingBranch(repoWorking);
            freeze.setDeliveryState("NONE");
            freeze.setFrozenAt(frozenAt);
            taskRepositoryFreezeMapper.insert(freeze);
        }

        recordHistory(taskId, null, "PENDING", "任务创建并冻结仓库集合", user.userId());

        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "TASK_CREATE",
                "TASK",
                taskId,
                project.getId(),
                requirementId,
                taskId,
                null,
                "创建任务：" + task.getTitle(),
                Map.of(
                        "code", task.getCode(),
                        "branchName", task.getBranchName(),
                        "frozenRepoCount", repos.size(),
                        "baseBranches",
                        repos.stream()
                                .map(r -> Map.of(
                                        "localDirectoryName", r.getLocalDirectoryName(),
                                        "baseBranch",
                                        r.getDefaultBranch() == null || r.getDefaultBranch().isBlank()
                                                ? "main"
                                                : r.getDefaultBranch()))
                                .toList()),
                "SUCCESS");

        lifecycleService.syncAutomaticStatus(requirementId);
        return toView(task, project.getName(), frozenRepos(taskId));
    }

    @Transactional
    public TaskDetailView cancel(AuthUser user, String taskId, TaskActionRequest request) {
        CodingTask task = requireTask(user, taskId);
        if (TERMINAL.contains(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "终态任务不能取消");
        }
        return transition(user, task, "CANCELLED", "用户取消", "TASK_CANCEL", request);
    }

    @Transactional
    public TaskDetailView abandon(AuthUser user, String taskId, TaskActionRequest request) {
        CodingTask task = requireTask(user, taskId);
        if (!"WAITING_USER".equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有 WAITING_USER 才能明确放弃");
        }
        return transition(user, task, "FAILED", "用户明确放弃", "TASK_ABANDON", request);
    }

    @Transactional
    public TaskDetailView approve(AuthUser user, String taskId, TaskActionRequest request) {
        CodingTask task = requireTask(user, taskId);
        if (!"WAITING_APPROVAL".equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有 WAITING_APPROVAL 才能审核通过");
        }
        return transition(user, task, "COMPLETED", "审核通过，不自动合并", "TASK_APPROVE", request);
    }

    private TaskDetailView transition(
            AuthUser user,
            CodingTask task,
            String toStatus,
            String reason,
            String action,
            TaskActionRequest request) {
        String from = task.getStatus();
        task.setStatus(toStatus);
        if (TERMINAL.contains(toStatus)) {
            task.setTerminalAt(Instant.now());
        }
        codingTaskMapper.updateById(task);
        recordHistory(task.getId(), from, toStatus, reason + noteSuffix(request), user.userId());
        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                action,
                "TASK",
                task.getId(),
                task.getProjectId(),
                task.getRequirementId(),
                task.getId(),
                null,
                reason + "：" + task.getTitle(),
                Map.of(
                        "fromStatus", from,
                        "toStatus", toStatus,
                        "note", request == null || request.note() == null ? "" : request.note()),
                "SUCCESS");
        if ("COMPLETED".equals(toStatus)) {
            lifecycleService.onTaskCompleted(task);
        } else if ("CANCELLED".equals(toStatus) || "FAILED".equals(toStatus)) {
            lifecycleService.onTaskCancelledOrFailed(task);
        } else {
            lifecycleService.syncAutomaticStatus(task.getRequirementId());
        }
        return get(user, task.getId());
    }


    @Transactional
    public TaskDetailView update(AuthUser user, String taskId, UpdateTaskRequest request) {
        CodingTask task = requireTask(user, taskId);
        if (TERMINAL.contains(task.getStatus()) || "WAITING_APPROVAL".equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前任务状态不能修改配置");
        }
        if (request.title() != null && !request.title().isBlank()) {
            task.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            task.setDescription(blankToNull(request.description()));
        }
        if (request.priority() != null && !request.priority().isBlank()) {
            task.setPriority(request.priority());
        }
        if (request.predecessorTaskId() != null) {
            String predecessor = blankToNull(request.predecessorTaskId());
            if (predecessor != null) {
                CodingTask parent = codingTaskMapper.selectById(predecessor);
                if (parent == null || !task.getRequirementId().equals(parent.getRequirementId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "前置任务必须属于同一需求");
                }
            }
            task.setPredecessorTaskId(predecessor);
        }
        if (request.modelProviderId() != null) {
            task.setModelProviderId(blankToNull(request.modelProviderId()));
        }
        if (request.modelId() != null) {
            task.setModelId(blankToNull(request.modelId()));
        }
        if (request.agentProfileId() != null) {
            task.setAgentProfileId(blankToNull(request.agentProfileId()));
        }
        boolean canRewriteCheckout = "PENDING".equals(task.getStatus()) || "PREPARING".equals(task.getStatus());
        if (request.workingBranch() != null && !request.workingBranch().isBlank()) {
            if (!canRewriteCheckout) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务启动后不能再改工作分支名");
            }
            String working = sanitizeBranch(request.workingBranch());
            task.setBranchName(working);
        }
        codingTaskMapper.updateById(task);
        if (canRewriteCheckout && request.repositories() != null) {
            rewriteCheckouts(task, request.repositories(), task.getBranchName());
        } else if (canRewriteCheckout && request.workingBranch() != null && !request.workingBranch().isBlank()) {
            List<TaskRepositoryFreeze> freezes = taskRepositoryFreezeMapper.selectList(
                    new LambdaQueryWrapper<TaskRepositoryFreeze>().eq(TaskRepositoryFreeze::getTaskId, task.getId()));
            for (TaskRepositoryFreeze freeze : freezes) {
                freeze.setWorkingBranch(task.getBranchName());
                taskRepositoryFreezeMapper.updateById(freeze);
            }
        }
        recordHistory(task.getId(), task.getStatus(), task.getStatus(), "更新任务配置", user.userId());
        return get(user, task.getId());
    }

    public TaskDefaultsView defaults(AuthUser user, String requirementId) {
        Requirement requirement = requireRequirement(user, requirementId);
        Project project = projectAccess.requireProjectMember(user, requirement.getProjectId());
        String nextCode = nextTaskCode(project.getCode());
        java.util.LinkedHashSet<String> taken = takenWorkingBranches(project.getId(), null);
        String working = WorkingBranchNames.defaultName(requirement.getCode(), nextCode, taken);
        List<ProjectRepository> repos = projectRepositoryMapper.selectList(new LambdaQueryWrapper<ProjectRepository>()
                .eq(ProjectRepository::getProjectId, project.getId())
                .orderByAsc(ProjectRepository::getLocalDirectoryName));
        List<RepoDefaultView> repoViews = repos.stream()
                .map(repo -> new RepoDefaultView(
                        repo.getId(),
                        repo.getDisplayName(),
                        repo.getLocalDirectoryName(),
                        repo.getCloneUrl(),
                        repo.getProviderType(),
                        repo.getDefaultBranch() == null || repo.getDefaultBranch().isBlank()
                                ? "main"
                                : repo.getDefaultBranch(),
                        repo.getDefaultBranch() == null || repo.getDefaultBranch().isBlank()
                                ? "main"
                                : repo.getDefaultBranch(),
                        working))
                .toList();
        return new TaskDefaultsView(working, taken.contains(WorkingBranchNames.fromRequirementCode(requirement.getCode())), nextCode, repoViews);
    }

    private void rewriteCheckouts(CodingTask task, List<RepoCheckoutBinding> bindings, String fallbackWorking) {
        List<ProjectRepository> repos = projectRepositoryMapper.selectList(new LambdaQueryWrapper<ProjectRepository>()
                .eq(ProjectRepository::getProjectId, task.getProjectId())
                .orderByAsc(ProjectRepository::getLocalDirectoryName));
        List<ProjectRepository> selected = selectRepositories(repos, bindings);
        List<TaskRepositoryFreeze> existing = taskRepositoryFreezeMapper.selectList(
                new LambdaQueryWrapper<TaskRepositoryFreeze>().eq(TaskRepositoryFreeze::getTaskId, task.getId()));
        java.util.Map<String, TaskRepositoryFreeze> byRepo = existing.stream()
                .collect(java.util.stream.Collectors.toMap(TaskRepositoryFreeze::getProjectRepositoryId, item -> item, (a, b) -> a));
        Instant frozenAt = Instant.now();
        java.util.Set<String> keep = new java.util.HashSet<>();
        for (ProjectRepository repo : selected) {
            RepoCheckoutBinding binding = bindingFor(bindings, repo.getId());
            String source = resolveSourceBranch(binding, repo);
            String repoWorking = binding != null && notBlank(binding.workingBranch())
                    ? sanitizeBranch(binding.workingBranch())
                    : fallbackWorking;
            TaskRepositoryFreeze freeze = byRepo.get(repo.getId());
            if (freeze == null) {
                freeze = new TaskRepositoryFreeze();
                freeze.setId(UUID.randomUUID().toString());
                freeze.setTaskId(task.getId());
                freeze.setProjectRepositoryId(repo.getId());
                freeze.setDeliveryState("NONE");
                freeze.setFrozenAt(frozenAt);
                freeze.setBaseBranch(source);
                freeze.setWorkingBranch(repoWorking);
                taskRepositoryFreezeMapper.insert(freeze);
            } else {
                freeze.setBaseBranch(source);
                freeze.setWorkingBranch(repoWorking);
                taskRepositoryFreezeMapper.updateById(freeze);
            }
            keep.add(repo.getId());
        }
        for (TaskRepositoryFreeze freeze : existing) {
            if (!keep.contains(freeze.getProjectRepositoryId()) && "NONE".equals(freeze.getDeliveryState())) {
                taskRepositoryFreezeMapper.deleteById(freeze.getId());
            }
        }
    }

    private java.util.LinkedHashSet<String> takenWorkingBranches(String projectId, String excludeTaskId) {
        java.util.LinkedHashSet<String> taken = new java.util.LinkedHashSet<>();
        List<CodingTask> tasks = codingTaskMapper.selectList(new LambdaQueryWrapper<CodingTask>()
                .eq(CodingTask::getProjectId, projectId)
                .ne(CodingTask::getStatus, "CANCELLED"));
        if (tasks == null) {
            return taken;
        }
        for (CodingTask existing : tasks) {
            if (excludeTaskId != null && excludeTaskId.equals(existing.getId())) {
                continue;
            }
            if (notBlank(existing.getBranchName())) {
                taken.add(existing.getBranchName());
            }
        }
        if (tasks.isEmpty()) {
            return taken;
        }
        List<String> ids = tasks.stream()
                .map(CodingTask::getId)
                .filter(id -> excludeTaskId == null || !excludeTaskId.equals(id))
                .toList();
        if (ids.isEmpty()) {
            return taken;
        }
        List<TaskRepositoryFreeze> freezes = taskRepositoryFreezeMapper.selectList(
                new LambdaQueryWrapper<TaskRepositoryFreeze>().in(TaskRepositoryFreeze::getTaskId, ids));
        if (freezes != null) {
            for (TaskRepositoryFreeze freeze : freezes) {
                if (notBlank(freeze.getWorkingBranch())) {
                    taken.add(freeze.getWorkingBranch());
                }
            }
        }
        return taken;
    }

    private static String resolveWorkingBranch(
            String requested, String requirementCode, String taskCode, java.util.Set<String> taken) {
        if (notBlank(requested)) {
            return sanitizeBranch(requested);
        }
        return WorkingBranchNames.defaultName(requirementCode, taskCode, taken);
    }

    private static String resolveSourceBranch(RepoCheckoutBinding binding, ProjectRepository repo) {
        if (binding != null && notBlank(binding.sourceBranch())) {
            return sanitizeBranch(binding.sourceBranch());
        }
        if (repo.getDefaultBranch() == null || repo.getDefaultBranch().isBlank()) {
            return "main";
        }
        return repo.getDefaultBranch();
    }

    private static String sanitizeBranch(String raw) {
        try {
            return WorkingBranchNames.sanitizeFull(raw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static RepoCheckoutBinding bindingFor(List<RepoCheckoutBinding> bindings, String repoId) {
        if (bindings == null) {
            return null;
        }
        for (RepoCheckoutBinding binding : bindings) {
            if (binding != null && repoId.equals(binding.projectRepositoryId())) {
                return binding;
            }
        }
        return null;
    }

    private static List<ProjectRepository> selectRepositories(
            List<ProjectRepository> repos, List<RepoCheckoutBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return repos;
        }
        java.util.Map<String, ProjectRepository> byId = repos.stream()
                .collect(java.util.stream.Collectors.toMap(ProjectRepository::getId, item -> item, (a, b) -> a));
        List<ProjectRepository> selected = new java.util.ArrayList<>();
        for (RepoCheckoutBinding binding : bindings) {
            if (binding == null || binding.projectRepositoryId() == null) {
                continue;
            }
            ProjectRepository repo = byId.get(binding.projectRepositoryId());
            if (repo == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能选择当前项目已关联的仓库");
            }
            selected.add(repo);
        }
        if (selected.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "至少选择一个仓库");
        }
        return selected;
    }

    private CodingTask requireTask(AuthUser user, String taskId) {
        CodingTask task = codingTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在或无权查看");
        }
        projectAccess.requireProjectMember(user, task.getProjectId());
        return task;
    }

    private Requirement requireRequirement(AuthUser user, String requirementId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "需求不存在或无权查看");
        }
        projectAccess.requireProjectMember(user, requirement.getProjectId());
        return requirement;
    }

    private List<FrozenRepoView> frozenRepos(String taskId) {
        List<TaskRepositoryFreeze> freezes = taskRepositoryFreezeMapper.selectList(
                new LambdaQueryWrapper<TaskRepositoryFreeze>().eq(TaskRepositoryFreeze::getTaskId, taskId));
        if (freezes.isEmpty()) {
            return List.of();
        }
        Set<String> repoIds = new HashSet<>();
        for (TaskRepositoryFreeze freeze : freezes) {
            repoIds.add(freeze.getProjectRepositoryId());
        }
        Map<String, ProjectRepository> repos = projectRepositoryMapper
                .selectList(new LambdaQueryWrapper<ProjectRepository>().in(ProjectRepository::getId, repoIds))
                .stream()
                .collect(java.util.stream.Collectors.toMap(ProjectRepository::getId, r -> r));
        return freezes.stream()
                .map(freeze -> {
                    ProjectRepository repo = repos.get(freeze.getProjectRepositoryId());
                    return new FrozenRepoView(
                            freeze.getId(),
                            freeze.getProjectRepositoryId(),
                            repo == null ? null : repo.getDisplayName(),
                            repo == null ? null : repo.getLocalDirectoryName(),
                            repo == null ? null : repo.getCloneUrl(),
                            repo == null ? null : repo.getProviderType(),
                            repo == null ? null : repo.getDefaultBranch(),
                            freeze.getBaseBranch(),
                            freeze.getWorkingBranch() == null || freeze.getWorkingBranch().isBlank()
                                    ? null
                                    : freeze.getWorkingBranch(),
                            freeze.getDeliveryState());
                })
                .toList();
    }

    private List<MergeRequestView> openMergeRequests(String taskId, List<FrozenRepoView> frozen) {
        List<MergeRequestRecord> rows = mergeRequestMapper.selectList(new LambdaQueryWrapper<MergeRequestRecord>()
                .eq(MergeRequestRecord::getTaskId, taskId)
                .eq(MergeRequestRecord::getState, "OPEN"));
        return rows.stream()
                .map(row -> {
                    String dir = frozen.stream()
                            .filter(f -> f.projectRepositoryId().equals(row.getProjectRepositoryId()))
                            .map(FrozenRepoView::localDirectoryName)
                            .findFirst()
                            .orElse(row.getProjectRepositoryId());
                    return new MergeRequestView(
                            row.getProjectRepositoryId(),
                            dir,
                            row.getUrl(),
                            row.getExternalId(),
                            row.getHeadSha(),
                            row.getBranchName(),
                            row.getState());
                })
                .toList();
    }

    private UnsupportedActions unsupportedActions(CodingTask task) {
        // start/retry/request-changes 已由 TaskSessionService 接通；Codex 缺失时返回 NOT_AVAILABLE，不假装成功。
        String startReason = "可通过 POST /tasks/{id}/start 启动 Session（需执行节点与 codex）";
        boolean execution = false;
        if ("IMPLEMENT".equals(task.getTaskType()) || "BUGFIX".equals(task.getTaskType())) {
            try {
                lifecycleService.assertCanStart(task.getRequirementId(), task.getTaskType());
            } catch (ResponseStatusException ex) {
                execution = true;
                startReason = ex.getReason() == null ? "尚不满足开工门禁" : ex.getReason();
            }
        }
        boolean requestChanges = false;
        boolean retry = false;
        return new UnsupportedActions(
                execution,
                startReason,
                requestChanges,
                "可通过 POST /tasks/{id}/request-changes 在同一工作区开新 Session",
                retry,
                "可通过 POST /tasks/{id}/retry 在同一工作区开新 Session");
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
        taskStateHistoryMapper.insert(history);
    }

    private String nextTaskCode(String projectCode) {
        String prefix = "TASK-" + projectCode.toUpperCase() + "-";
        long count = codingTaskMapper.selectCount(new LambdaQueryWrapper<CodingTask>().likeRight(CodingTask::getCode, prefix));
        return prefix + (count + 1);
    }

    private TaskView toView(CodingTask task, String projectName, List<FrozenRepoView> frozen) {
        Requirement requirement = requirementMapper.selectById(task.getRequirementId());
        return new TaskView(
                task.getId(),
                task.getCode(),
                task.getRequirementId(),
                requirement == null ? null : requirement.getCode(),
                task.getProjectId(),
                projectName,
                task.getTitle(),
                task.getDescription(),
                task.getTaskType(),
                task.getPriority(),
                task.getStatus(),
                task.getBranchName(),
                task.getModelProviderId(),
                task.getModelId(),
                task.getAgentProfileId(),
                task.getCreatedBy(),
                task.getOwnerId(),
                task.getPredecessorTaskId(),
                task.getDefectId(),
                frozen.size());
    }

    private static String noteSuffix(TaskActionRequest request) {
        if (request == null || request.note() == null || request.note().isBlank()) {
            return "";
        }
        return "；说明：" + request.note().trim();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record TaskView(
            String id,
            String code,
            String requirementId,
            String requirementCode,
            String projectId,
            String projectName,
            String title,
            String description,
            String taskType,
            String priority,
            String status,
            String branchName,
            String modelProviderId,
            String modelId,
            String agentProfileId,
            String createdBy,
            String ownerId,
            String predecessorTaskId,
            String defectId,
            int frozenRepoCount) {
    }

    public record FrozenRepoView(
            String id,
            String projectRepositoryId,
            String displayName,
            String localDirectoryName,
            String cloneUrl,
            String providerType,
            String defaultBranch,
            String baseBranch,
            String workingBranch,
            String deliveryState) {
    }

    public record UnsupportedActions(
            boolean startOrRetryBlocked,
            String startOrRetryReason,
            boolean requestChangesBlocked,
            String requestChangesReason,
            boolean retryBlocked,
            String retryReason) {
    }

    public record MergeRequestView(
            String projectRepositoryId,
            String localDirectoryName,
            String url,
            String externalId,
            String headSha,
            String branchName,
            String state) {
    }

    public record TaskDetailView(
            TaskView task,
            List<FrozenRepoView> frozenRepositories,
            List<MergeRequestView> mergeRequests,
            List<String> testFileChanges,
            String testFileChangeHook,
            UnsupportedActions unsupported) {
    }

    public record RepoDefaultView(
            String projectRepositoryId,
            String displayName,
            String localDirectoryName,
            String cloneUrl,
            String providerType,
            String defaultBranch,
            String sourceBranch,
            String workingBranch) {}

    public record TaskDefaultsView(
            String workingBranch, boolean requirementBranchTaken, String nextTaskCode, List<RepoDefaultView> repositories) {}
}

