package com.forgepilot.project.workbench;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.AgentSession;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.persistence.AgentSessionMapper;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.Requirement;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.project.persistence.RequirementMapper;
import com.forgepilot.system.auth.AuthUser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkbenchService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final RequirementMapper requirementMapper;
    private final CodingTaskMapper codingTaskMapper;
    private final AgentSessionMapper agentSessionMapper;

    public WorkbenchService(
            ProjectMapper projectMapper,
            ProjectMemberMapper projectMemberMapper,
            RequirementMapper requirementMapper,
            CodingTaskMapper codingTaskMapper,
            AgentSessionMapper agentSessionMapper) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.requirementMapper = requirementMapper;
        this.codingTaskMapper = codingTaskMapper;
        this.agentSessionMapper = agentSessionMapper;
    }

    public WorkbenchSnapshot load(AuthUser user, WorkbenchScope scope) {
        if (user.tenantId() == null) {
            // 平台管理员默认不因管理身份看到未加入项目；无租户则空工作台。
            return empty(scope);
        }
        Set<String> memberships = new HashSet<>(projectMemberMapper.findProjectIdsByUserId(user.userId()));
        if (memberships.isEmpty()) {
            return empty(scope);
        }
        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getTenantId, user.tenantId())
                .in(Project::getId, memberships));
        Map<String, Project> projectById = projects.stream().collect(Collectors.toMap(Project::getId, p -> p));
        if (projectById.isEmpty()) {
            return empty(scope);
        }

        List<Requirement> requirements = requirementMapper.selectList(new LambdaQueryWrapper<Requirement>()
                .in(Requirement::getProjectId, projectById.keySet()));
        List<CodingTask> tasks = codingTaskMapper.selectList(new LambdaQueryWrapper<CodingTask>()
                .in(CodingTask::getProjectId, projectById.keySet()));
        Map<String, Requirement> requirementById = requirements.stream()
                .collect(Collectors.toMap(Requirement::getId, r -> r));
        Map<String, String> latestSessionByTask = latestSessionStatuses(tasks);

        List<WorkbenchTaskItem> visibleTasks = new ArrayList<>();
        for (CodingTask task : tasks) {
            Requirement parent = requirementById.get(task.getRequirementId());
            Project project = projectById.get(task.getProjectId());
            if (parent == null || project == null) {
                continue;
            }
            if (!WorkbenchAccess.canSeeProject(user.tenantId(), project.getTenantId(), memberships, project.getId())) {
                continue;
            }
            boolean mine = WorkbenchAccess.isMyTask(
                    user.userId(), task.getCreatedBy(), task.getOwnerId(), parent.getCreatedBy(), parent.getOwnerId());
            String sessionStatus = latestSessionByTask.get(task.getId());
            boolean waiting = mine && WorkbenchAccess.isWaitingOnMe(task.getStatus(), sessionStatus);
            boolean include = switch (scope) {
                case MEMBER -> true;
                case MINE -> mine;
                case WAITING -> waiting;
            };
            if (!include) {
                continue;
            }
            visibleTasks.add(new WorkbenchTaskItem(
                    task.getId(),
                    task.getCode(),
                    task.getRequirementId(),
                    parent.getCode(),
                    task.getTitle(),
                    task.getTaskType(),
                    task.getStatus(),
                    sessionStatus,
                    task.getCreatedBy(),
                    task.getOwnerId(),
                    task.getProjectId(),
                    project.getName(),
                    task.getBranchName(),
                    "Codex CLI",
                    false,
                    "INTERRUPTED".equals(sessionStatus)));
        }

        Set<String> waitingRequirementIds = visibleTasks.stream()
                .map(WorkbenchTaskItem::requirementId)
                .collect(Collectors.toSet());

        List<WorkbenchRequirementItem> visibleRequirements = new ArrayList<>();
        Map<String, int[]> progress = progressByRequirement(tasks);
        for (Requirement requirement : requirements) {
            Project project = projectById.get(requirement.getProjectId());
            if (project == null
                    || !WorkbenchAccess.canSeeProject(
                            user.tenantId(), project.getTenantId(), memberships, project.getId())) {
                continue;
            }
            boolean mine = WorkbenchAccess.isMyRequirement(
                    user.userId(), requirement.getCreatedBy(), requirement.getOwnerId());
            boolean waitingOnRequirement =
                    mine && WorkbenchAccess.isRequirementWaitingOnMe(requirement.getStatus());
            boolean include = switch (scope) {
                case MEMBER -> true;
                case MINE -> mine;
                case WAITING -> waitingRequirementIds.contains(requirement.getId()) || waitingOnRequirement;
            };
            if (!include) {
                continue;
            }
            int[] counts = progress.getOrDefault(requirement.getId(), new int[] {0, 0});
            visibleRequirements.add(new WorkbenchRequirementItem(
                    requirement.getId(),
                    requirement.getCode(),
                    requirement.getTitle(),
                    requirement.getStatus(),
                    requirement.getProjectId(),
                    project.getName(),
                    requirement.getCreatedBy(),
                    requirement.getOwnerId(),
                    requirement.getDescription() == null ? "" : requirement.getDescription(),
                    !"待分析".equals(requirement.getStatus()) && !"设计中".equals(requirement.getStatus()),
                    counts[0],
                    counts[1]));
        }

        return new WorkbenchSnapshot(
                scope.name().toLowerCase(),
                visibleRequirements,
                visibleTasks,
                counts(visibleRequirements, visibleTasks));
    }

    public WorkbenchRequirementItem requirement(AuthUser user, String requirementId) {
        WorkbenchSnapshot snapshot = load(user, WorkbenchScope.MEMBER);
        return snapshot.requirements().stream()
                .filter(item -> item.id().equals(requirementId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "需求不存在或无权查看"));
    }

    public WorkbenchTaskItem task(AuthUser user, String taskId) {
        WorkbenchSnapshot snapshot = load(user, WorkbenchScope.MEMBER);
        return snapshot.tasks().stream()
                .filter(item -> item.id().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在或无权查看"));
    }

    private Map<String, String> latestSessionStatuses(List<CodingTask> tasks) {
        Map<String, String> result = new HashMap<>();
        if (tasks.isEmpty()) {
            return result;
        }
        List<String> taskIds = tasks.stream().map(CodingTask::getId).toList();
        List<AgentSession> sessions = agentSessionMapper.selectList(new LambdaQueryWrapper<AgentSession>()
                .in(AgentSession::getTaskId, taskIds));
        for (AgentSession session : sessions) {
            // 骨架未存时间戳时，后写入的覆盖；活动会话优先。
            String current = result.get(session.getTaskId());
            if (current == null || isActive(session.getStatus())) {
                result.put(session.getTaskId(), session.getStatus());
            }
        }
        return result;
    }

    private static boolean isActive(String status) {
        return Set.of("CREATED", "STARTING", "RUNNING", "WAITING_INPUT").contains(status);
    }

    private static Map<String, int[]> progressByRequirement(List<CodingTask> tasks) {
        Map<String, int[]> progress = new HashMap<>();
        for (CodingTask task : tasks) {
            if ("CANCELLED".equals(task.getStatus())) {
                continue;
            }
            int[] counts = progress.computeIfAbsent(task.getRequirementId(), id -> new int[] {0, 0});
            counts[1]++;
            if ("COMPLETED".equals(task.getStatus())) {
                counts[0]++;
            }
        }
        return progress;
    }

    private static WorkbenchSnapshot.WorkbenchCounts counts(
            List<WorkbenchRequirementItem> requirements, List<WorkbenchTaskItem> tasks) {
        int running = 0;
        int verifying = 0;
        int approval = 0;
        int waitingUser = 0;
        for (WorkbenchTaskItem task : tasks) {
            if ("RUNNING".equals(task.status()) || "PREPARING".equals(task.status())) {
                running++;
            } else if ("VERIFYING".equals(task.status())) {
                verifying++;
            } else if ("WAITING_APPROVAL".equals(task.status())) {
                approval++;
            } else if ("WAITING_USER".equals(task.status())) {
                waitingUser++;
            }
        }
        return new WorkbenchSnapshot.WorkbenchCounts(
                requirements.size(), tasks.size(), running, verifying, approval, waitingUser);
    }

    private static WorkbenchSnapshot empty(WorkbenchScope scope) {
        return new WorkbenchSnapshot(
                scope.name().toLowerCase(),
                List.of(),
                List.of(),
                new WorkbenchSnapshot.WorkbenchCounts(0, 0, 0, 0, 0, 0));
    }
}
