package com.forgepilot.project.pmo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.service.TaskService;
import com.forgepilot.execution.service.TaskService.TaskView;
import com.forgepilot.execution.web.CreateTaskRequest;
import com.forgepilot.project.domain.PmoProposalDecision;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.Requirement;
import com.forgepilot.project.persistence.PmoProposalDecisionMapper;
import com.forgepilot.project.persistence.RequirementMapper;
import com.forgepilot.project.pmo.PmoProposalView.ConfirmResult;
import com.forgepilot.project.pmo.PmoProposalView.ListView;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.project.web.ConfirmProposalRequest;
import com.forgepilot.project.web.ReturnProposalRequest;
import com.forgepilot.project.workbench.WorkbenchProposalItem;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PmoProposalService {

    private final RequirementMapper requirementMapper;
    private final PmoProposalDecisionMapper decisionMapper;
    private final PmoSnapshotAssembler assembler;
    private final ProjectAccess projectAccess;
    private final TaskService taskService;
    private final ActivityLogRecorder activityLogRecorder;

    public PmoProposalService(
            RequirementMapper requirementMapper,
            PmoProposalDecisionMapper decisionMapper,
            PmoSnapshotAssembler assembler,
            ProjectAccess projectAccess,
            TaskService taskService,
            ActivityLogRecorder activityLogRecorder) {
        this.requirementMapper = requirementMapper;
        this.decisionMapper = decisionMapper;
        this.assembler = assembler;
        this.projectAccess = projectAccess;
        this.taskService = taskService;
        this.activityLogRecorder = activityLogRecorder;
    }

    public ListView listForRequirement(AuthUser user, String requirementId) {
        Requirement requirement = requireVisible(user, requirementId);
        Project project = projectAccess.requireProjectMember(user, requirement.getProjectId());
        PmoSnapshot snapshot = assembler.assemble(requirement, project, assembler.tasksOf(requirementId));
        return new ListView(toViews(openProposals(snapshot), requirement, project.getName(), Map.of()));
    }

    public List<WorkbenchProposalItem> listOpenTaskDrafts(
            List<Requirement> requirements,
            Map<String, Project> projectById,
            List<CodingTask> tasks,
            Predicate<Requirement> include) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }
        List<Requirement> scoped = include == null
                ? requirements
                : requirements.stream().filter(include).toList();
        if (scoped.isEmpty()) {
            return List.of();
        }
        List<PmoSnapshot> snapshots = assembler.assemble(scoped, projectById, tasks);
        Map<String, Set<String>> dismissed = dismissedByRequirement(
                scoped.stream().map(Requirement::getId).toList());
        Map<String, Requirement> requirementById =
                scoped.stream().collect(Collectors.toMap(Requirement::getId, item -> item));
        List<WorkbenchProposalItem> items = new ArrayList<>();
        for (PmoSnapshot snapshot : snapshots) {
            Requirement requirement = requirementById.get(snapshot.requirementId());
            Project project = projectById.get(snapshot.projectId());
            String projectName = project == null ? "" : project.getName();
            for (PmoCandidate candidate :
                    PmoPlanner.open(PmoPlanner.plan(snapshot), dismissed.getOrDefault(snapshot.requirementId(), Set.of()))) {
                if (!candidate.confirmCreatesTask()) {
                    continue;
                }
                items.add(new WorkbenchProposalItem(
                        candidate.proposalId(),
                        candidate.kind(),
                        candidate.taskType(),
                        candidate.defectId(),
                        candidate.title(),
                        candidate.hint(),
                        candidate.primaryActionLabel(),
                        true,
                        snapshot.requirementId(),
                        requirement == null ? snapshot.requirementCode() : requirement.getCode(),
                        requirement == null ? snapshot.requirementTitle() : requirement.getTitle(),
                        snapshot.projectId(),
                        projectName));
            }
        }
        return items;
    }

    @Transactional
    public ConfirmResult confirm(
            AuthUser user, String requirementId, String proposalId, ConfirmProposalRequest request) {
        Requirement requirement = requireVisible(user, requirementId);
        Project project = projectAccess.requireProjectMember(user, requirement.getProjectId());

        PmoProposalDecision existing = findDecision(requirementId, proposalId);
        if (existing != null && "CONFIRMED".equals(existing.getDecision()) && notBlank(existing.getCreatedTaskId())) {
            TaskView task = taskService.get(user, existing.getCreatedTaskId()).task();
            PmoCandidate candidate = reopenCandidate(requirement, project, proposalId);
            return new ConfirmResult(
                    toView(
                            candidate != null ? candidate : fallbackCandidate(existing),
                            requirement,
                            project.getName(),
                            task.title()),
                    task);
        }
        if (existing != null && "RETURNED".equals(existing.getDecision())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这条建议已退回");
        }

        PmoSnapshot snapshot = assembler.assemble(requirement, project, assembler.tasksOf(requirementId));
        PmoCandidate candidate = findOpen(snapshot, proposalId);
        if (!candidate.confirmCreatesTask() || !PmoPlanner.KIND_CREATE_TASK.equals(candidate.kind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该建议需要你在需求页完成对应确认动作，不能当作创建任务来确认");
        }

        String title = request != null && notBlank(request.title()) ? request.title().trim() : candidate.title();
        if (!notBlank(title)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写任务标题");
        }

        TaskView task = taskService.create(
                user,
                requirementId,
                new CreateTaskRequest(
                        title, null, candidate.taskType(), "MEDIUM", user.userId(), null, candidate.defectId()));

        PmoProposalDecision decision = newDecision(
                user,
                project,
                requirement,
                candidate,
                "CONFIRMED",
                request == null ? null : request.comment(),
                task.id());
        try {
            decisionMapper.insert(decision);
        } catch (DataIntegrityViolationException ex) {
            PmoProposalDecision raced = findDecision(requirementId, proposalId);
            if (raced != null && "CONFIRMED".equals(raced.getDecision()) && notBlank(raced.getCreatedTaskId())) {
                return new ConfirmResult(
                        toView(candidate, requirement, project.getName(), title),
                        taskService.get(user, raced.getCreatedTaskId()).task());
            }
            throw ex;
        }

        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "PMO_PROPOSAL_CONFIRM",
                "REQUIREMENT",
                requirementId,
                project.getId(),
                requirementId,
                task.id(),
                null,
                actorName(user) + " 确认平台建议并创建任务：" + task.title(),
                Map.of(
                        "proposalId",
                        proposalId,
                        "kind",
                        candidate.kind(),
                        "taskType",
                        candidate.taskType(),
                        "taskId",
                        task.id(),
                        "taskStatus",
                        task.status(),
                        "actorUsername",
                        nullToEmpty(user.username())),
                "SUCCESS");
        return new ConfirmResult(toView(candidate, requirement, project.getName(), title), task);
    }

    @Transactional
    public PmoProposalView dismiss(
            AuthUser user, String requirementId, String proposalId, ReturnProposalRequest request) {
        Requirement requirement = requireVisible(user, requirementId);
        Project project = projectAccess.requireProjectMember(user, requirement.getProjectId());

        PmoProposalDecision existing = findDecision(requirementId, proposalId);
        if (existing != null && "CONFIRMED".equals(existing.getDecision())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这条建议已确认创建任务，不能再退回");
        }
        PmoSnapshot snapshot = assembler.assemble(requirement, project, assembler.tasksOf(requirementId));
        PmoCandidate candidate;
        if (existing != null && "RETURNED".equals(existing.getDecision())) {
            candidate = reopenCandidate(requirement, project, proposalId);
            if (candidate == null) {
                candidate = fallbackCandidate(existing);
            }
            return toView(candidate, requirement, project.getName(), candidate.title());
        }

        candidate = findOpen(snapshot, proposalId);
        PmoProposalDecision decision = newDecision(
                user,
                project,
                requirement,
                candidate,
                "RETURNED",
                request == null ? null : request.comment(),
                null);
        try {
            decisionMapper.insert(decision);
        } catch (DataIntegrityViolationException ex) {
            PmoProposalDecision raced = findDecision(requirementId, proposalId);
            if (raced != null && "RETURNED".equals(raced.getDecision())) {
                return toView(candidate, requirement, project.getName(), candidate.title());
            }
            if (raced != null && "CONFIRMED".equals(raced.getDecision())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "这条建议已确认创建任务，不能再退回");
            }
            throw ex;
        }

        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "PMO_PROPOSAL_RETURN",
                "REQUIREMENT",
                requirementId,
                project.getId(),
                requirementId,
                null,
                null,
                actorName(user) + " 退回平台建议：" + (notBlank(candidate.title()) ? candidate.title() : candidate.kind()),
                Map.of(
                        "proposalId",
                        proposalId,
                        "kind",
                        candidate.kind(),
                        "taskType",
                        nullToEmpty(candidate.taskType()),
                        "comment",
                        request == null || request.comment() == null ? "" : request.comment(),
                        "actorUsername",
                        nullToEmpty(user.username())),
                "SUCCESS");
        return toView(candidate, requirement, project.getName(), candidate.title());
    }

    private List<PmoCandidate> openProposals(PmoSnapshot snapshot) {
        return PmoPlanner.open(
                PmoPlanner.plan(snapshot),
                dismissedByRequirement(List.of(snapshot.requirementId()))
                        .getOrDefault(snapshot.requirementId(), Set.of()));
    }

    private PmoCandidate findOpen(PmoSnapshot snapshot, String proposalId) {
        return openProposals(snapshot).stream()
                .filter(candidate -> proposalId.equals(candidate.proposalId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "这条建议已经过时，请刷新后再看"));
    }

    private PmoCandidate reopenCandidate(Requirement requirement, Project project, String proposalId) {
        PmoSnapshot snapshot = assembler.assemble(requirement, project, assembler.tasksOf(requirement.getId()));
        return PmoPlanner.plan(snapshot).stream()
                .filter(candidate -> proposalId.equals(candidate.proposalId()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Set<String>> dismissedByRequirement(Collection<String> requirementIds) {
        if (requirementIds == null || requirementIds.isEmpty()) {
            return Map.of();
        }
        List<PmoProposalDecision> rows = decisionMapper.selectList(new LambdaQueryWrapper<PmoProposalDecision>()
                .in(PmoProposalDecision::getRequirementId, requirementIds)
                .eq(PmoProposalDecision::getDecision, "RETURNED"));
        Map<String, Set<String>> dismissed = new HashMap<>();
        for (PmoProposalDecision row : rows) {
            dismissed.computeIfAbsent(row.getRequirementId(), id -> new java.util.HashSet<>())
                    .add(row.getProposalId());
        }
        return dismissed;
    }

    private PmoProposalDecision findDecision(String requirementId, String proposalId) {
        List<PmoProposalDecision> rows = decisionMapper.selectList(new LambdaQueryWrapper<PmoProposalDecision>()
                .eq(PmoProposalDecision::getRequirementId, requirementId)
                .eq(PmoProposalDecision::getProposalId, proposalId)
                .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private PmoProposalDecision newDecision(
            AuthUser user,
            Project project,
            Requirement requirement,
            PmoCandidate candidate,
            String decision,
            String comment,
            String createdTaskId) {
        PmoProposalDecision row = new PmoProposalDecision();
        row.setId(UUID.randomUUID().toString());
        row.setTenantId(user.tenantId());
        row.setProjectId(project.getId());
        row.setRequirementId(requirement.getId());
        row.setProposalId(candidate.proposalId());
        row.setFingerprint(candidate.fingerprint());
        row.setKind(candidate.kind());
        row.setTaskType(candidate.taskType());
        row.setDefectId(candidate.defectId());
        row.setDecision(decision);
        row.setComment(blankToNull(comment));
        row.setCreatedTaskId(createdTaskId);
        row.setActorUserId(user.userId());
        row.setCreatedAt(Instant.now());
        return row;
    }

    private List<PmoProposalView> toViews(
            List<PmoCandidate> candidates,
            Requirement requirement,
            String projectName,
            Map<String, String> titleOverrides) {
        return candidates.stream()
                .map(candidate -> toView(
                        candidate,
                        requirement,
                        projectName,
                        titleOverrides.getOrDefault(candidate.proposalId(), candidate.title())))
                .toList();
    }

    private static PmoProposalView toView(
            PmoCandidate candidate, Requirement requirement, String projectName, String title) {
        return new PmoProposalView(
                candidate.proposalId(),
                candidate.kind(),
                candidate.taskType(),
                candidate.defectId(),
                candidate.targetTaskId(),
                title == null ? candidate.title() : title,
                candidate.hint(),
                candidate.primaryActionLabel(),
                candidate.confirmCreatesTask(),
                requirement.getId(),
                requirement.getCode(),
                requirement.getTitle(),
                requirement.getProjectId(),
                projectName);
    }

    private static PmoCandidate fallbackCandidate(PmoProposalDecision decision) {
        return new PmoCandidate(
                decision.getKind(),
                decision.getTaskType(),
                decision.getDefectId(),
                null,
                "",
                "",
                "",
                decision.getFingerprint(),
                decision.getProposalId(),
                PmoPlanner.KIND_CREATE_TASK.equals(decision.getKind()));
    }

    private Requirement requireVisible(AuthUser user, String requirementId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "需求不存在或无权查看");
        }
        projectAccess.requireProjectMember(user, requirement.getProjectId());
        return requirement;
    }

    private static String actorName(AuthUser user) {
        if (notBlank(user.username())) {
            return user.username();
        }
        if (notBlank(user.displayName())) {
            return user.displayName();
        }
        return user.userId();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return notBlank(value) ? value.trim() : null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
