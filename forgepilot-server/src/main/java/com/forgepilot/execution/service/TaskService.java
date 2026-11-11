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
import com.forgepilot.execution.web.CreateTaskRequest;
import com.forgepilot.execution.web.TaskActionRequest;
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
                unsupportedActions(task.getStatus()));
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
        CodingTask task = new CodingTask();
        task.setId(taskId);
        task.setProjectId(project.getId());
        task.setRequirementId(requirementId);
        task.setCode(nextTaskCode(project.getCode()));
        task.setTitle(request.title().trim());
        task.setDescription(blankToNull(request.description()));
        task.setTaskType(request.taskType());
        task.setPriority(request.priority() == null || request.priority().isBlank() ? "MEDIUM" : request.priority());
        task.setBranchName("fp/" + taskId);
        task.setStatus("PENDING");
        task.setOwnerId(request.ownerId() == null || request.ownerId().isBlank() ? user.userId() : request.ownerId());
        task.setPredecessorTaskId(blankToNull(request.predecessorTaskId()));
        task.setDefectId("BUGFIX".equals(request.taskType()) ? blankToNull(request.defectId()) : null);
        task.setCreatedBy(user.userId());
        codingTaskMapper.insert(task);

        Instant frozenAt = Instant.now();
        for (ProjectRepository repo : repos) {
            TaskRepositoryFreeze freeze = new TaskRepositoryFreeze();
            freeze.setId(UUID.randomUUID().toString());
            freeze.setTaskId(taskId);
            freeze.setProjectRepositoryId(repo.getId());
            freeze.setBaseBranch(
                    repo.getDefaultBranch() == null || repo.getDefaultBranch().isBlank()
                            ? "main"
                            : repo.getDefaultBranch());
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
                            freeze.getBaseBranch(),
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

    private UnsupportedActions unsupportedActions(String status) {
        // start/retry/request-changes 已由 TaskSessionService 接通；Codex 缺失时返回 NOT_AVAILABLE，不假装成功。
        boolean execution = false;
        boolean requestChanges = false;
        boolean retry = false;
        return new UnsupportedActions(
                execution,
                "可通过 POST /tasks/{id}/start 启动 Session（需执行节点与 codex）",
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
        return new TaskView(
                task.getId(),
                task.getCode(),
                task.getRequirementId(),
                task.getProjectId(),
                projectName,
                task.getTitle(),
                task.getDescription(),
                task.getTaskType(),
                task.getPriority(),
                task.getStatus(),
                task.getBranchName(),
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
            String projectId,
            String projectName,
            String title,
            String description,
            String taskType,
            String priority,
            String status,
            String branchName,
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
            String baseBranch,
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
}

