package com.forgepilot.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.execution.service.TaskService;
import com.forgepilot.execution.web.CreateTaskRequest;
import com.forgepilot.project.domain.AnalysisDraft;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.Requirement;
import com.forgepilot.project.persistence.AnalysisDraftMapper;
import com.forgepilot.project.persistence.RequirementMapper;
import com.forgepilot.project.web.ConfirmAnalysisRequest;
import com.forgepilot.project.web.CreateRequirementRequest;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequirementService {

    private final RequirementMapper requirementMapper;
    private final AnalysisDraftMapper analysisDraftMapper;
    private final CodingTaskMapper codingTaskMapper;
    private final ProjectAccess projectAccess;
    private final ActivityLogRecorder activityLogRecorder;
    private final TaskService taskService;

    public RequirementService(
            RequirementMapper requirementMapper,
            AnalysisDraftMapper analysisDraftMapper,
            CodingTaskMapper codingTaskMapper,
            ProjectAccess projectAccess,
            ActivityLogRecorder activityLogRecorder,
            @Lazy TaskService taskService) {
        this.requirementMapper = requirementMapper;
        this.analysisDraftMapper = analysisDraftMapper;
        this.codingTaskMapper = codingTaskMapper;
        this.projectAccess = projectAccess;
        this.activityLogRecorder = activityLogRecorder;
        this.taskService = taskService;
    }

    public List<RequirementView> listByProject(AuthUser user, String projectId) {
        Project project = projectAccess.requireProjectMember(user, projectId);
        return requirementMapper
                .selectList(new LambdaQueryWrapper<Requirement>()
                        .eq(Requirement::getProjectId, projectId)
                        .orderByDesc(Requirement::getCode))
                .stream()
                .map(r -> toView(r, project.getName()))
                .toList();
    }

    public RequirementDetailView get(AuthUser user, String requirementId) {
        Requirement requirement = requireVisible(user, requirementId);
        Project project = projectAccess.requireProjectInTenant(user, requirement.getProjectId());
        AnalysisDraft draft = latestDraft(requirementId);
        List<TaskService.TaskView> tasks = taskService.listByRequirement(user, requirementId);
        return new RequirementDetailView(
                toView(requirement, project.getName()),
                draft == null ? "" : draft.getBody(),
                draft != null && draft.getConfirmedAt() != null,
                draft == null ? null : draft.getConfirmedBy(),
                tasks);
    }

    @Transactional
    public RequirementView create(AuthUser user, String projectId, CreateRequirementRequest request) {
        Project project = projectAccess.requireProjectMember(user, projectId);
        if ("ARCHIVED".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "归档项目不能创建需求");
        }
        Requirement requirement = new Requirement();
        requirement.setId(UUID.randomUUID().toString());
        requirement.setProjectId(projectId);
        requirement.setCode(nextRequirementCode(project.getCode()));
        requirement.setTitle(request.title().trim());
        requirement.setDescription(blankToNull(request.description()));
        requirement.setBackground(blankToNull(request.background()));
        requirement.setScope(blankToNull(request.scope()));
        requirement.setAcceptance(blankToNull(request.acceptance()));
        requirement.setPriority(request.priority() == null || request.priority().isBlank() ? "MEDIUM" : request.priority());
        requirement.setOwnerId(request.ownerId() == null || request.ownerId().isBlank() ? user.userId() : request.ownerId());
        requirement.setStatus("待分析");
        requirement.setStatusLocked(false);
        requirement.setCreatedBy(user.userId());
        requirementMapper.insert(requirement);

        if (requirement.getDescription() != null) {
            AnalysisDraft draft = new AnalysisDraft();
            draft.setId(UUID.randomUUID().toString());
            draft.setRequirementId(requirement.getId());
            draft.setBody(requirement.getDescription());
            draft.setCreatedBy(user.userId());
            analysisDraftMapper.insert(draft);
        }

        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "REQUIREMENT_CREATE",
                "REQUIREMENT",
                requirement.getId(),
                projectId,
                requirement.getId(),
                null,
                null,
                "创建需求：" + requirement.getTitle(),
                Map.of("code", requirement.getCode(), "title", requirement.getTitle()),
                "SUCCESS");
        return toView(requirement, project.getName());
    }

    @Transactional
    public RequirementDetailView confirmAnalysis(AuthUser user, String requirementId, ConfirmAnalysisRequest request) {
        Requirement requirement = requireVisible(user, requirementId);
        if (!"待分析".equals(requirement.getStatus()) && !"设计中".equals(requirement.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前需求状态不允许确认分析草稿");
        }
        Project project = projectAccess.requireProjectMember(user, requirement.getProjectId());

        AnalysisDraft draft = latestDraft(requirementId);
        if (draft == null) {
            draft = new AnalysisDraft();
            draft.setId(UUID.randomUUID().toString());
            draft.setRequirementId(requirementId);
            draft.setCreatedBy(user.userId());
            draft.setBody(request.draft());
            draft.setConfirmedBy(user.userId());
            draft.setConfirmedAt(Instant.now());
            analysisDraftMapper.insert(draft);
        } else {
            draft.setBody(request.draft());
            draft.setConfirmedBy(user.userId());
            draft.setConfirmedAt(Instant.now());
            analysisDraftMapper.updateById(draft);
        }

        requirement.setDescription(request.draft());
        requirement.setStatus("待开发");
        requirementMapper.updateById(requirement);

        List<String> createdTaskIds = new ArrayList<>();
        if (request.taskTitles() != null) {
            for (String title : request.taskTitles()) {
                if (title == null || title.isBlank()) {
                    continue;
                }
                TaskService.TaskView created = taskService.create(
                        user,
                        requirementId,
                        new CreateTaskRequest(title.trim(), null, "IMPLEMENT", "MEDIUM", user.userId(), null));
                createdTaskIds.add(created.id());
            }
        }

        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "REQUIREMENT_CONFIRM_ANALYSIS",
                "REQUIREMENT",
                requirementId,
                project.getId(),
                requirementId,
                null,
                null,
                "确认分析与任务拆分：" + requirement.getTitle(),
                Map.of("createdTaskIds", createdTaskIds, "taskCount", createdTaskIds.size()),
                "SUCCESS");

        return get(user, requirementId);
    }

    public Requirement requireVisible(AuthUser user, String requirementId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "需求不存在或无权查看");
        }
        projectAccess.requireProjectMember(user, requirement.getProjectId());
        return requirement;
    }

    private AnalysisDraft latestDraft(String requirementId) {
        List<AnalysisDraft> drafts = analysisDraftMapper.selectList(new LambdaQueryWrapper<AnalysisDraft>()
                .eq(AnalysisDraft::getRequirementId, requirementId)
                .orderByDesc(AnalysisDraft::getId)
                .last("limit 1"));
        return drafts.isEmpty() ? null : drafts.get(0);
    }

    private String nextRequirementCode(String projectCode) {
        String prefix = "REQ-" + projectCode.toUpperCase() + "-";
        long count = requirementMapper.selectCount(new LambdaQueryWrapper<Requirement>()
                .likeRight(Requirement::getCode, prefix));
        return prefix + (count + 1);
    }

    private RequirementView toView(Requirement requirement, String projectName) {
        int[] progress = progress(requirement.getId());
        AnalysisDraft draft = latestDraft(requirement.getId());
        boolean confirmed = (draft != null && draft.getConfirmedAt() != null)
                || (!"待分析".equals(requirement.getStatus()) && !"设计中".equals(requirement.getStatus()));
        return new RequirementView(
                requirement.getId(),
                requirement.getCode(),
                requirement.getTitle(),
                requirement.getStatus(),
                requirement.getPriority(),
                requirement.getProjectId(),
                projectName,
                requirement.getCreatedBy(),
                requirement.getOwnerId(),
                requirement.getDescription() == null ? "" : requirement.getDescription(),
                confirmed,
                progress[0],
                progress[1]);
    }

    private int[] progress(String requirementId) {
        List<CodingTask> tasks = codingTaskMapper.selectList(new LambdaQueryWrapper<CodingTask>()
                .eq(CodingTask::getRequirementId, requirementId));
        int total = 0;
        int done = 0;
        for (CodingTask task : tasks) {
            if ("CANCELLED".equals(task.getStatus())) {
                continue;
            }
            total++;
            if ("COMPLETED".equals(task.getStatus())) {
                done++;
            }
        }
        return new int[] {done, total};
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record RequirementView(
            String id,
            String code,
            String title,
            String status,
            String priority,
            String projectId,
            String projectName,
            String createdBy,
            String ownerId,
            String draft,
            boolean confirmed,
            int taskDone,
            int taskTotal) {
    }

    public record RequirementDetailView(
            RequirementView requirement,
            String draft,
            boolean confirmed,
            String confirmedBy,
            List<TaskService.TaskView> tasks) {
    }
}
