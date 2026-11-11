package com.forgepilot.project.lifecycle;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.delivery.port.GitHostingPort;
import com.forgepilot.delivery.service.TaskVerifyDeliveryService;
import com.forgepilot.execution.domain.TaskRepositoryFreeze;
import com.forgepilot.execution.persistence.TaskRepositoryFreezeMapper;
import com.forgepilot.project.domain.RequirementReleaseTarget;
import com.forgepilot.project.persistence.RequirementReleaseTargetMapper;
import com.forgepilot.project.service.RepositoryBranchService;
import com.forgepilot.project.web.CreateReleaseRequest.ReleaseTargetRequest;
import com.forgepilot.project.domain.AnalysisDraft;
import com.forgepilot.project.domain.Defect;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.ProjectRepository;
import com.forgepilot.project.domain.Requirement;
import com.forgepilot.project.domain.RequirementDocument;
import com.forgepilot.project.domain.RequirementRelease;
import com.forgepilot.project.domain.RequirementReleaseItem;
import com.forgepilot.project.domain.Review;
import com.forgepilot.project.lifecycle.RequirementStatusMachine.DefectSnapshot;
import com.forgepilot.project.lifecycle.RequirementStatusMachine.GateSnapshot;
import com.forgepilot.project.lifecycle.RequirementStatusMachine.TaskSnapshot;
import com.forgepilot.project.persistence.AnalysisDraftMapper;
import com.forgepilot.project.persistence.DefectMapper;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectRepositoryMapper;
import com.forgepilot.project.persistence.RequirementDocumentMapper;
import com.forgepilot.project.persistence.RequirementMapper;
import com.forgepilot.project.persistence.RequirementReleaseItemMapper;
import com.forgepilot.project.persistence.RequirementReleaseMapper;
import com.forgepilot.project.persistence.ReviewMapper;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.project.web.AcceptRequirementRequest;
import com.forgepilot.project.web.CreateDefectRequest;
import com.forgepilot.project.web.CreateReleaseRequest;
import com.forgepilot.project.web.DefectActionRequest;
import com.forgepilot.project.web.ReviewDecisionRequest;
import com.forgepilot.project.web.SaveDocumentRequest;
import com.forgepilot.project.web.UpdateRequirementStatusRequest;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequirementLifecycleService {

    private final RequirementMapper requirementMapper;
    private final AnalysisDraftMapper analysisDraftMapper;
    private final ProjectMapper projectMapper;
    private final ProjectRepositoryMapper projectRepositoryMapper;
    private final ReviewMapper reviewMapper;
    private final RequirementDocumentMapper documentMapper;
    private final DefectMapper defectMapper;
    private final RequirementReleaseMapper releaseMapper;
    private final RequirementReleaseItemMapper releaseItemMapper;
    private final CodingTaskMapper codingTaskMapper;
    private final TaskRepositoryFreezeMapper freezeMapper;
    private final RequirementReleaseTargetMapper releaseTargetMapper;
    private final GitHostingPort gitHostingPort;
    private final RepositoryBranchService repositoryBranchService;
    private final ProjectAccess projectAccess;
    private final ActivityLogRecorder activityLogRecorder;

    public RequirementLifecycleService(
            RequirementMapper requirementMapper,
            AnalysisDraftMapper analysisDraftMapper,
            ProjectMapper projectMapper,
            ProjectRepositoryMapper projectRepositoryMapper,
            ReviewMapper reviewMapper,
            RequirementDocumentMapper documentMapper,
            DefectMapper defectMapper,
            RequirementReleaseMapper releaseMapper,
            RequirementReleaseItemMapper releaseItemMapper,
            CodingTaskMapper codingTaskMapper,
            TaskRepositoryFreezeMapper freezeMapper,
            RequirementReleaseTargetMapper releaseTargetMapper,
            GitHostingPort gitHostingPort,
            RepositoryBranchService repositoryBranchService,
            ProjectAccess projectAccess,
            ActivityLogRecorder activityLogRecorder) {
        this.requirementMapper = requirementMapper;
        this.analysisDraftMapper = analysisDraftMapper;
        this.projectMapper = projectMapper;
        this.projectRepositoryMapper = projectRepositoryMapper;
        this.reviewMapper = reviewMapper;
        this.documentMapper = documentMapper;
        this.defectMapper = defectMapper;
        this.releaseMapper = releaseMapper;
        this.releaseItemMapper = releaseItemMapper;
        this.codingTaskMapper = codingTaskMapper;
        this.freezeMapper = freezeMapper;
        this.releaseTargetMapper = releaseTargetMapper;
        this.gitHostingPort = gitHostingPort;
        this.repositoryBranchService = repositoryBranchService;
        this.projectAccess = projectAccess;
        this.activityLogRecorder = activityLogRecorder;
    }

    public LifecycleBundle bundle(String requirementId) {
        Requirement requirement = requireRequirement(requirementId);
        List<ReviewView> requirementReviews = reviews("REQUIREMENT", requirementId);
        List<DocumentView> documents = documents(requirementId);
        RequirementDocument current = currentDocument(requirementId);
        List<ReviewView> designReviews = current == null
                ? documents.stream()
                        .flatMap(doc -> reviews("DESIGN_DOCUMENT", doc.id()).stream())
                        .toList()
                : reviews("DESIGN_DOCUMENT", current.getId());
        List<DefectView> defects = defects(requirementId);
        List<ReleaseView> releases = releasesForRequirement(requirementId);
        GateView gates = toGateView(requirement, current, tasksOf(requirementId), defectsOf(requirementId));
        return new LifecycleBundle(requirementReviews, documents, designReviews, defects, releases, gates);
    }

    public GateView gatesFor(String requirementId) {
        Requirement requirement = requireRequirement(requirementId);
        return toGateView(
                requirement, currentDocument(requirementId), tasksOf(requirementId), defectsOf(requirementId));
    }

    @Transactional
    public ReviewView reviewRequirement(AuthUser user, String requirementId, ReviewDecisionRequest request) {
        Requirement requirement = requireMemberRequirement(user, requirementId);
        if (!"待需求评审".equals(requirement.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有处于「待需求评审」的需求才能做需求评审");
        }
        Review review = insertReview(user, requirement.getProjectId(), "REQUIREMENT", requirementId, request);
        String next = RequirementStatusMachine.afterRequirementReview(request.decision());
        requirement.setStatus(next);
        requirement.setStatusLocked(false);
        requirementMapper.updateById(requirement);
        if ("REJECTED".equals(request.decision())) {
            clearAnalysisConfirmation(requirementId);
        }
        record(
                user,
                "REQUIREMENT_REVIEW",
                "REVIEW",
                review.getId(),
                requirement,
                "需求评审" + decisionLabel(request.decision()) + "：" + requirement.getTitle(),
                Map.of(
                        "decision", request.decision(),
                        "comment", nullToEmpty(request.comment()),
                        "toStatus", next));
        return toReviewView(review);
    }

    @Transactional
    public DocumentView saveDesignDocument(AuthUser user, String requirementId, SaveDocumentRequest request) {
        Requirement requirement = requireMemberRequirement(user, requirementId);
        if ("已取消".equals(requirement.getStatus())
                || "已完成".equals(requirement.getStatus())
                || "已发布".equals(requirement.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前需求状态不能再修改设计文档");
        }
        if (!"待需求评审".equals(requirement.getStatus())
                && !"设计中".equals(requirement.getStatus())
                && !"待设计评审".equals(requirement.getStatus())
                && !"待开发".equals(requirement.getStatus())
                && !"开发中".equals(requirement.getStatus())
                && !"测试中".equals(requirement.getStatus())
                && !"待验收".equals(requirement.getStatus())) {
            // 待分析：需求评审尚未通过，不能进入设计
            if ("待分析".equals(requirement.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "需求评审通过后才能编写设计文档");
            }
        }
        latestRequirementReviewApproved(requirementId);
        RequirementDocument previous = currentDocument(requirementId);
        int nextVersion = previous == null ? 1 : previous.getVersionNo() + 1;
        if (previous != null) {
            previous.setCurrentFlag(false);
            previous.setPendingReview(false);
            previous.setUpdatedBy(user.userId());
            previous.setUpdatedAt(Instant.now());
            documentMapper.updateById(previous);
        }
        RequirementDocument document = new RequirementDocument();
        document.setId(UUID.randomUUID().toString());
        document.setRequirementId(requirementId);
        document.setKind("DESIGN");
        document.setTitle(request.title().trim());
        document.setBody(request.body() == null ? "" : request.body());
        document.setVersionNo(nextVersion);
        document.setCurrentFlag(true);
        document.setPendingReview(false);
        document.setCreatedBy(user.userId());
        document.setCreatedAt(Instant.now());
        document.setUpdatedBy(user.userId());
        document.setUpdatedAt(Instant.now());
        documentMapper.insert(document);
        if ("待开发".equals(requirement.getStatus())
                || "待设计评审".equals(requirement.getStatus())) {
            requirement.setStatus("设计中");
            requirementMapper.updateById(requirement);
        }
        record(
                user,
                "DESIGN_DOCUMENT_SAVE",
                "DESIGN_DOCUMENT",
                document.getId(),
                requirement,
                "保存设计文档 v" + nextVersion + "：" + document.getTitle(),
                Map.of("versionNo", nextVersion, "title", document.getTitle()));
        return toDocumentView(document);
    }

    @Transactional
    public DocumentView submitDesignReview(AuthUser user, String requirementId) {
        Requirement requirement = requireMemberRequirement(user, requirementId);
        latestRequirementReviewApproved(requirementId);
        RequirementDocument current = currentDocument(requirementId);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先编写设计文档再提交评审");
        }
        if (!"设计中".equals(requirement.getStatus()) && !"待设计评审".equals(requirement.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前需求状态不能提交设计评审");
        }
        current.setPendingReview(true);
        current.setUpdatedBy(user.userId());
        current.setUpdatedAt(Instant.now());
        documentMapper.updateById(current);
        requirement.setStatus(RequirementStatusMachine.afterSubmitDesignReview());
        requirementMapper.updateById(requirement);
        record(
                user,
                "DESIGN_DOCUMENT_SUBMIT_REVIEW",
                "DESIGN_DOCUMENT",
                current.getId(),
                requirement,
                "提交设计文档评审：v" + current.getVersionNo(),
                Map.of("documentId", current.getId(), "versionNo", current.getVersionNo()));
        return toDocumentView(current);
    }

    @Transactional
    public ReviewView reviewDesignDocument(AuthUser user, String requirementId, ReviewDecisionRequest request) {
        Requirement requirement = requireMemberRequirement(user, requirementId);
        if (!"待设计评审".equals(requirement.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有处于「待设计评审」的需求才能做设计评审");
        }
        RequirementDocument current = currentDocument(requirementId);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有可评审的设计文档");
        }
        Review review = insertReview(user, requirement.getProjectId(), "DESIGN_DOCUMENT", current.getId(), request);
        current.setPendingReview(false);
        current.setUpdatedBy(user.userId());
        current.setUpdatedAt(Instant.now());
        documentMapper.updateById(current);
        String next = RequirementStatusMachine.afterDesignReview(request.decision());
        requirement.setStatus(next);
        requirementMapper.updateById(requirement);
        record(
                user,
                "DESIGN_REVIEW",
                "REVIEW",
                review.getId(),
                requirement,
                "设计文档评审" + decisionLabel(request.decision()) + "：v" + current.getVersionNo(),
                Map.of(
                        "decision",
                        request.decision(),
                        "comment",
                        nullToEmpty(request.comment()),
                        "documentId",
                        current.getId(),
                        "versionNo",
                        current.getVersionNo(),
                        "toStatus",
                        next));
        return toReviewView(review);
    }

    @Transactional
    public RequirementViewStatus updateStatus(AuthUser user, String requirementId, UpdateRequirementStatusRequest request) {
        Requirement requirement = requireMemberRequirement(user, requirementId);
        String next = request.status().trim();
        if (!RequirementStatusMachine.canManuallySet(next)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能手工把需求标为该状态；「已发布」只能通过发布进入");
        }
        String from = requirement.getStatus();
        requirement.setStatus(next);
        requirement.setStatusLocked(RequirementStatusMachine.isAutoLocked(next));
        requirementMapper.updateById(requirement);
        record(
                user,
                "REQUIREMENT_STATUS_UPDATE",
                "REQUIREMENT",
                requirementId,
                requirement,
                "人工修改需求状态：" + from + " → " + next,
                Map.of("fromStatus", from, "toStatus", next, "note", nullToEmpty(request.note())));
        return new RequirementViewStatus(requirement.getId(), requirement.getStatus(), Boolean.TRUE.equals(requirement.getStatusLocked()));
    }

    @Transactional
    public RequirementViewStatus accept(AuthUser user, String requirementId, AcceptRequirementRequest request) {
        Requirement requirement = requireMemberRequirement(user, requirementId);
        boolean blocking = hasBlockingDefects(requirementId);
        if (Boolean.TRUE.equals(request.accepted())) {
            if (!RequirementStatusMachine.canAccept(requirement.getStatus(), blocking)) {
                if (!"待验收".equals(requirement.getStatus())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有「待验收」的需求才能验收通过");
                }
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "存在未关闭的缺陷（OPEN 或 IN_FIX），不能验收通过");
            }
            requirement.setStatus("已完成");
            requirement.setStatusLocked(true);
            requirementMapper.updateById(requirement);
            record(
                    user,
                    "REQUIREMENT_ACCEPT",
                    "REQUIREMENT",
                    requirementId,
                    requirement,
                    "验收通过：" + requirement.getTitle(),
                    Map.of("comment", nullToEmpty(request.comment()), "toStatus", "已完成"));
        } else {
            if (!"待验收".equals(requirement.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有「待验收」的需求才能退回");
            }
            String next = RequirementStatusMachine.afterAcceptance(false, blocking);
            requirement.setStatus(next);
            requirement.setStatusLocked(false);
            requirementMapper.updateById(requirement);
            record(
                    user,
                    "REQUIREMENT_REJECT_ACCEPTANCE",
                    "REQUIREMENT",
                    requirementId,
                    requirement,
                    "验收退回：" + requirement.getTitle(),
                    Map.of("comment", nullToEmpty(request.comment()), "toStatus", next, "hasBlockingDefects", blocking));
        }
        return new RequirementViewStatus(
                requirement.getId(), requirement.getStatus(), Boolean.TRUE.equals(requirement.getStatusLocked()));
    }

    @Transactional
    public DefectView createDefect(AuthUser user, String requirementId, CreateDefectRequest request) {
        Requirement requirement = requireMemberRequirement(user, requirementId);
        if ("已取消".equals(requirement.getStatus()) || "已发布".equals(requirement.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前需求状态不能登记缺陷");
        }
        Project project = projectMapper.selectById(requirement.getProjectId());
        if (request.taskId() != null && !request.taskId().isBlank()) {
            CodingTask task = codingTaskMapper.selectById(request.taskId());
            if (task == null || !requirementId.equals(task.getRequirementId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联任务必须属于同一需求");
            }
        }
        Defect defect = new Defect();
        defect.setId(UUID.randomUUID().toString());
        defect.setProjectId(requirement.getProjectId());
        defect.setRequirementId(requirementId);
        defect.setTaskId(blankToNull(request.taskId()));
        defect.setCode(nextDefectCode(project.getCode()));
        defect.setTitle(request.title().trim());
        defect.setDescription(blankToNull(request.description()));
        defect.setSeverity(
                request.severity() == null || request.severity().isBlank() ? "MEDIUM" : request.severity());
        defect.setStatus("OPEN");
        defect.setCreatedBy(user.userId());
        defect.setCreatedAt(Instant.now());
        defect.setUpdatedBy(user.userId());
        defect.setUpdatedAt(Instant.now());
        defectMapper.insert(defect);
        record(
                user,
                "DEFECT_CREATE",
                "DEFECT",
                defect.getId(),
                requirement,
                "登记缺陷：" + defect.getCode() + " " + defect.getTitle(),
                Map.of("code", defect.getCode(), "severity", defect.getSeverity()));
        syncAutomaticStatus(requirementId);
        return toDefectView(defect);
    }

    @Transactional
    public DefectView closeDefect(AuthUser user, String defectId, DefectActionRequest request) {
        return transitionDefect(user, defectId, "CLOSED", List.of("VERIFIED", "FIXED"), request, "关闭缺陷");
    }

    @Transactional
    public DefectView rejectDefect(AuthUser user, String defectId, DefectActionRequest request) {
        return transitionDefect(user, defectId, "REJECTED", List.of("OPEN", "IN_FIX", "FIXED", "VERIFIED"), request, "驳回缺陷");
    }

    @Transactional
    public DefectView verifyDefect(AuthUser user, String defectId, DefectActionRequest request) {
        return transitionDefect(user, defectId, "VERIFIED", List.of("FIXED"), request, "回归验证通过");
    }

    @Transactional
    public ReleaseView createRelease(AuthUser user, String projectId, CreateReleaseRequest request) {
        Project project = projectAccess.requireProjectMember(user, projectId);
        List<String> requirementIds = request.requirementIds() == null
                ? List.of()
                : request.requirementIds().stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (requirementIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发布至少需要关联一个已完成的需求");
        }
        List<Requirement> requirements = new ArrayList<>();
        for (String requirementId : requirementIds) {
            Requirement requirement = requirementMapper.selectById(requirementId);
            if (requirement == null || !projectId.equals(requirement.getProjectId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发布只能关联本项目的需求");
            }
            if (!RequirementStatusMachine.canRelease(requirement.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "需求 " + requirement.getCode() + " 尚未验收完成，不能发布");
            }
            requirements.add(requirement);
        }
        String version = request.version().trim();
        Long existing = releaseMapper.selectCount(new LambdaQueryWrapper<RequirementRelease>()
                .eq(RequirementRelease::getProjectId, projectId)
                .eq(RequirementRelease::getVersion, version));
        if (existing != null && existing > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该版本号已经发布过");
        }
        List<MergeJob> jobs = collectMergeJobs(requirementIds);
        List<PerformedMerge> merges = mergeForRelease(projectId, version, jobs, request.targets());
        RequirementRelease release = new RequirementRelease();
        release.setId(UUID.randomUUID().toString());
        release.setProjectId(projectId);
        release.setVersion(version);
        release.setNotes(blankToNull(request.notes()));
        release.setReleasedBy(user.userId());
        release.setReleasedAt(Instant.now());
        releaseMapper.insert(release);
        for (Requirement requirement : requirements) {
            RequirementReleaseItem item = new RequirementReleaseItem();
            item.setId(UUID.randomUUID().toString());
            item.setReleaseId(release.getId());
            item.setRequirementId(requirement.getId());
            releaseItemMapper.insert(item);
            requirement.setStatus("已发布");
            requirement.setStatusLocked(true);
            requirementMapper.updateById(requirement);
        }
        for (PerformedMerge merge : merges) {
            RequirementReleaseTarget target = new RequirementReleaseTarget();
            target.setId(UUID.randomUUID().toString());
            target.setReleaseId(release.getId());
            target.setProjectRepositoryId(merge.projectRepositoryId());
            target.setSourceBranch(merge.sourceBranch());
            target.setTargetBranch(merge.targetBranch());
            target.setMergeSha(merge.mergeSha());
            target.setStatus(merge.alreadyMerged() ? "ALREADY_MERGED" : "MERGED");
            target.setMessage(merge.message());
            target.setCreatedAt(Instant.now());
            releaseTargetMapper.insert(target);
        }
        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "REQUIREMENT_RELEASE",
                "RELEASE",
                release.getId(),
                projectId,
                requirements.get(0).getId(),
                null,
                null,
                "发布 " + version + "，包含 " + requirements.size() + " 条需求，合并 " + merges.size() + " 个分支",
                Map.of(
                        "version",
                        version,
                        "requirementIds",
                        requirementIds,
                        "notes",
                        nullToEmpty(request.notes()),
                        "targets",
                        merges.stream()
                                .map(item -> Map.of(
                                        "projectRepositoryId",
                                        item.projectRepositoryId(),
                                        "sourceBranch",
                                        item.sourceBranch(),
                                        "targetBranch",
                                        item.targetBranch(),
                                        "status",
                                        item.alreadyMerged() ? "ALREADY_MERGED" : "MERGED"))
                                .toList()),
                "SUCCESS");
        return toReleaseView(release, requirements, merges);
    }


    public ReleasePlanView releasePlan(AuthUser user, String projectId, List<String> requirementIds) {
        projectAccess.requireProjectMember(user, projectId);
        List<String> ids = requirementIds == null
                ? List.of()
                : requirementIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要发布的需求");
        }
        for (String requirementId : ids) {
            Requirement requirement = requirementMapper.selectById(requirementId);
            if (requirement == null || !projectId.equals(requirement.getProjectId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发布只能关联本项目的需求");
            }
        }
        List<MergeJob> jobs = collectMergeJobs(ids);
        Map<String, List<String>> sources = new java.util.LinkedHashMap<>();
        for (MergeJob job : jobs) {
            sources.computeIfAbsent(job.projectRepositoryId(), key -> new ArrayList<>()).add(job.sourceBranch());
        }
        List<ReleaseRepoPlan> plans = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : sources.entrySet()) {
            ProjectRepository repo = projectRepositoryMapper.selectById(entry.getKey());
            if (repo == null) {
                continue;
            }
            var branches = repositoryBranchService.list(repo);
            plans.add(new ReleaseRepoPlan(
                    repo.getId(),
                    repo.getDisplayName(),
                    repo.getLocalDirectoryName(),
                    repo.getCloneUrl(),
                    branches.defaultBranch(),
                    entry.getValue(),
                    branches.branches().stream().map(RepositoryBranchService.BranchItem::name).toList(),
                    branches.error()));
        }
        return new ReleasePlanView(plans);
    }

    public List<ReleaseView> listReleases(AuthUser user, String projectId) {
        projectAccess.requireProjectMember(user, projectId);
        return releaseMapper
                .selectList(new LambdaQueryWrapper<RequirementRelease>()
                        .eq(RequirementRelease::getProjectId, projectId)
                        .orderByDesc(RequirementRelease::getReleasedAt))
                .stream()
                .map(this::toReleaseView)
                .toList();
    }

    public void assertCanStart(String requirementId, String taskType) {
        if (!RequirementStatusMachine.requiresStartGate(taskType)) {
            return;
        }
        Requirement requirement = requireRequirement(requirementId);
        GateView gates = toGateView(
                requirement, currentDocument(requirementId), tasksOf(requirementId), defectsOf(requirementId));
        if (!gates.blockers().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "尚不满足开工门禁：" + String.join("；", gates.blockers()));
        }
    }

    @Transactional
    public void onDevelopmentStarted(CodingTask task) {
        if (task == null) {
            return;
        }
        if ("BUGFIX".equals(task.getTaskType())) {
            Defect defect = requireLinkedDefect(task);
            if ("CLOSED".equals(defect.getStatus()) || "REJECTED".equals(defect.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已关闭或已驳回的缺陷不能再启动修复任务");
            }
            defect.setStatus("IN_FIX");
            defect.setTaskId(task.getId());
            defect.setUpdatedAt(Instant.now());
            defectMapper.updateById(defect);
        }
        syncAutomaticStatus(task.getRequirementId());
    }

    @Transactional
    public void onTaskCompleted(CodingTask task) {
        if (task == null) {
            return;
        }
        if ("BUGFIX".equals(task.getTaskType()) && task.getDefectId() != null) {
            Defect defect = defectMapper.selectById(task.getDefectId());
            if (defect != null && ("IN_FIX".equals(defect.getStatus()) || "OPEN".equals(defect.getStatus()))) {
                defect.setStatus("FIXED");
                defect.setUpdatedAt(Instant.now());
                defectMapper.updateById(defect);
            }
        }
        if ("TEST".equals(task.getTaskType())) {
            List<Defect> fixed = defectMapper.selectList(new LambdaQueryWrapper<Defect>()
                    .eq(Defect::getRequirementId, task.getRequirementId())
                    .eq(Defect::getStatus, "FIXED"));
            for (Defect defect : fixed) {
                defect.setStatus("VERIFIED");
                defect.setUpdatedAt(Instant.now());
                defectMapper.updateById(defect);
            }
        }
        syncAutomaticStatus(task.getRequirementId());
    }

    @Transactional
    public void onTaskCancelledOrFailed(CodingTask task) {
        if (task == null) {
            return;
        }
        if ("BUGFIX".equals(task.getTaskType()) && task.getDefectId() != null) {
            Defect defect = defectMapper.selectById(task.getDefectId());
            if (defect != null && "IN_FIX".equals(defect.getStatus()) && task.getId().equals(defect.getTaskId())) {
                defect.setStatus("OPEN");
                defect.setUpdatedAt(Instant.now());
                defectMapper.updateById(defect);
            }
        }
        syncAutomaticStatus(task.getRequirementId());
    }

    @Transactional
    public void syncAutomaticStatus(String requirementId) {
        if (requirementId == null || requirementId.isBlank()) {
            return;
        }
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            return;
        }
        if (Boolean.TRUE.equals(requirement.getStatusLocked())
                || RequirementStatusMachine.isAutoLocked(requirement.getStatus())) {
            return;
        }
        String next = RequirementStatusMachine.autoStatus(
                requirement.getStatus(), tasksOf(requirementId), hasBlockingDefects(requirementId));
        if (next != null && !next.equals(requirement.getStatus())) {
            requirement.setStatus(next);
            requirementMapper.updateById(requirement);
        }
    }

    public Defect requireDefectForBugfix(String requirementId, String defectId) {
        if (defectId == null || defectId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BUGFIX 任务必须关联缺陷");
        }
        Defect defect = defectMapper.selectById(defectId);
        if (defect == null || !requirementId.equals(defect.getRequirementId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺陷不存在或不属于当前需求");
        }
        if ("CLOSED".equals(defect.getStatus()) || "REJECTED".equals(defect.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已关闭或已驳回的缺陷不能再创建修复任务");
        }
        return defect;
    }

    private DefectView transitionDefect(
            AuthUser user,
            String defectId,
            String toStatus,
            List<String> allowedFrom,
            DefectActionRequest request,
            String summary) {
        Defect defect = defectMapper.selectById(defectId);
        if (defect == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "缺陷不存在或无权查看");
        }
        Requirement requirement = requireMemberRequirement(user, defect.getRequirementId());
        if (!allowedFrom.contains(defect.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "当前缺陷状态不能" + summary + "：" + defect.getStatus());
        }
        String from = defect.getStatus();
        defect.setStatus(toStatus);
        defect.setUpdatedBy(user.userId());
        defect.setUpdatedAt(Instant.now());
        defectMapper.updateById(defect);
        record(
                user,
                "DEFECT_STATUS",
                "DEFECT",
                defect.getId(),
                requirement,
                summary + "：" + defect.getCode(),
                Map.of(
                        "fromStatus",
                        from,
                        "toStatus",
                        toStatus,
                        "note",
                        request == null ? "" : nullToEmpty(request.note())));
        syncAutomaticStatus(requirement.getId());
        return toDefectView(defect);
    }


    private void clearAnalysisConfirmation(String requirementId) {
        List<AnalysisDraft> drafts = analysisDraftMapper.selectList(new LambdaQueryWrapper<AnalysisDraft>()
                .eq(AnalysisDraft::getRequirementId, requirementId)
                .orderByDesc(AnalysisDraft::getId)
                .last("limit 1"));
        if (drafts.isEmpty()) {
            return;
        }
        AnalysisDraft draft = drafts.get(0);
        draft.setConfirmedAt(null);
        draft.setConfirmedBy(null);
        analysisDraftMapper.updateById(draft);
    }
    private void latestRequirementReviewApproved(String requirementId) {
        Review latest = latestReview("REQUIREMENT", requirementId);
        if (latest == null || !"APPROVED".equals(latest.getDecision())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "需求评审通过后才能进入设计");
        }
    }

    private Review insertReview(
            AuthUser user, String projectId, String subjectType, String subjectId, ReviewDecisionRequest request) {
        Review review = new Review();
        review.setId(UUID.randomUUID().toString());
        review.setProjectId(projectId);
        review.setSubjectType(subjectType);
        review.setSubjectId(subjectId);
        review.setDecision(request.decision());
        review.setComment(blankToNull(request.comment()));
        review.setReviewerId(user.userId());
        review.setCreatedAt(Instant.now());
        reviewMapper.insert(review);
        return review;
    }

    private GateView toGateView(
            Requirement requirement,
            RequirementDocument current,
            List<TaskSnapshot> tasks,
            List<DefectSnapshot> defects) {
        Project project = projectMapper.selectById(requirement.getProjectId());
        Review requirementReview = latestReview("REQUIREMENT", requirement.getId());
        boolean requirementApproved = requirementReview != null && "APPROVED".equals(requirementReview.getDecision());
        boolean designExists = current != null;
        Review designReview = current == null ? null : latestReview("DESIGN_DOCUMENT", current.getId());
        boolean designApproved = designReview != null && "APPROVED".equals(designReview.getDecision());
        boolean codingToolBound = project != null && notBlank(project.getDefaultAgentProfileId());
        boolean modelBound = project != null
                && notBlank(project.getDefaultModelProviderId())
                && notBlank(project.getDefaultModelId());
        long repoCount = project == null
                ? 0
                : projectRepositoryMapper.selectCount(new LambdaQueryWrapper<ProjectRepository>()
                        .eq(ProjectRepository::getProjectId, requirement.getProjectId()));
        boolean repoBound = repoCount != 0;
        GateSnapshot snapshot = new GateSnapshot(
                requirementApproved, designExists, designApproved, codingToolBound, modelBound, repoBound);
        List<String> blockers = RequirementStatusMachine.startBlockers("IMPLEMENT", snapshot);
        int stageIndex = RequirementStatusMachine.currentStageIndex(requirement.getStatus(), snapshot, tasks, defects);
        String stage = RequirementStatusMachine.STAGES.get(Math.min(stageIndex, RequirementStatusMachine.STAGES.size() - 1));
        return new GateView(
                requirementApproved,
                designExists,
                designApproved,
                codingToolBound,
                modelBound,
                repoBound,
                snapshot.canStartImplementOrBugfix(),
                blockers,
                stageIndex,
                stage,
                toReviewView(requirementReview),
                toReviewView(designReview));
    }

    private List<ReviewView> reviews(String subjectType, String subjectId) {
        return reviewMapper
                .selectList(new LambdaQueryWrapper<Review>()
                        .eq(Review::getSubjectType, subjectType)
                        .eq(Review::getSubjectId, subjectId)
                        .orderByDesc(Review::getCreatedAt)
                        .orderByDesc(Review::getId))
                .stream()
                .map(this::toReviewView)
                .toList();
    }

    private Review latestReview(String subjectType, String subjectId) {
        List<Review> rows = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getSubjectType, subjectType)
                .eq(Review::getSubjectId, subjectId)
                .orderByDesc(Review::getCreatedAt)
                .orderByDesc(Review::getId)
                .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<DocumentView> documents(String requirementId) {
        return documentMapper
                .selectList(new LambdaQueryWrapper<RequirementDocument>()
                        .eq(RequirementDocument::getRequirementId, requirementId)
                        .eq(RequirementDocument::getKind, "DESIGN")
                        .orderByDesc(RequirementDocument::getVersionNo))
                .stream()
                .map(this::toDocumentView)
                .toList();
    }

    private RequirementDocument currentDocument(String requirementId) {
        List<RequirementDocument> rows = documentMapper.selectList(new LambdaQueryWrapper<RequirementDocument>()
                .eq(RequirementDocument::getRequirementId, requirementId)
                .eq(RequirementDocument::getKind, "DESIGN")
                .eq(RequirementDocument::getCurrentFlag, true)
                .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<DefectView> defects(String requirementId) {
        return defectsEntities(requirementId).stream().map(this::toDefectView).toList();
    }

    private List<Defect> defectsEntities(String requirementId) {
        return defectMapper.selectList(new LambdaQueryWrapper<Defect>()
                .eq(Defect::getRequirementId, requirementId)
                .orderByDesc(Defect::getCode));
    }

    private boolean hasBlockingDefects(String requirementId) {
        return defectsEntities(requirementId).stream()
                .anyMatch(d -> RequirementStatusMachine.BLOCKING_DEFECT_STATUSES.contains(d.getStatus()));
    }

    private List<TaskSnapshot> tasksOf(String requirementId) {
        return codingTaskMapper
                .selectList(new LambdaQueryWrapper<CodingTask>().eq(CodingTask::getRequirementId, requirementId))
                .stream()
                .map(t -> new TaskSnapshot(t.getTaskType(), t.getStatus()))
                .toList();
    }

    private List<DefectSnapshot> defectsOf(String requirementId) {
        return defectsEntities(requirementId).stream()
                .map(d -> new DefectSnapshot(d.getStatus()))
                .toList();
    }

    private List<ReleaseView> releasesForRequirement(String requirementId) {
        List<RequirementReleaseItem> items = releaseItemMapper.selectList(
                new LambdaQueryWrapper<RequirementReleaseItem>()
                        .eq(RequirementReleaseItem::getRequirementId, requirementId));
        if (items.isEmpty()) {
            return List.of();
        }
        List<String> ids = items.stream().map(RequirementReleaseItem::getReleaseId).toList();
        return releaseMapper
                .selectList(new LambdaQueryWrapper<RequirementRelease>().in(RequirementRelease::getId, ids))
                .stream()
                .map(this::toReleaseView)
                .toList();
    }

    private ReleaseView toReleaseView(RequirementRelease release) {
        List<RequirementReleaseItem> items = releaseItemMapper.selectList(
                new LambdaQueryWrapper<RequirementReleaseItem>()
                        .eq(RequirementReleaseItem::getReleaseId, release.getId()));
        List<Requirement> requirements = items.isEmpty()
                ? List.of()
                : requirementMapper.selectList(new LambdaQueryWrapper<Requirement>()
                        .in(
                                Requirement::getId,
                                items.stream().map(RequirementReleaseItem::getRequirementId).toList()));
        return toReleaseView(release, requirements);
    }

    private ReleaseView toReleaseView(RequirementRelease release, List<Requirement> requirements) {
        return toReleaseView(release, requirements, loadPerformed(release.getId()));
    }

    private ReleaseView toReleaseView(
            RequirementRelease release, List<Requirement> requirements, List<PerformedMerge> merges) {
        return new ReleaseView(
                release.getId(),
                release.getProjectId(),
                release.getVersion(),
                release.getNotes() == null ? "" : release.getNotes(),
                release.getReleasedBy(),
                release.getReleasedAt(),
                requirements.stream().map(Requirement::getId).toList(),
                requirements.stream().map(Requirement::getCode).toList(),
                merges.stream()
                        .map(item -> new ReleaseTargetView(
                                item.projectRepositoryId(),
                                item.sourceBranch(),
                                item.targetBranch(),
                                item.mergeSha(),
                                item.alreadyMerged() ? "ALREADY_MERGED" : item.status(),
                                item.message()))
                        .toList());
    }

    private List<PerformedMerge> loadPerformed(String releaseId) {
        if (releaseTargetMapper == null) {
            return List.of();
        }
        List<RequirementReleaseTarget> rows = releaseTargetMapper.selectList(
                new LambdaQueryWrapper<RequirementReleaseTarget>()
                        .eq(RequirementReleaseTarget::getReleaseId, releaseId));
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new PerformedMerge(
                        row.getProjectRepositoryId(),
                        row.getSourceBranch(),
                        row.getTargetBranch(),
                        row.getMergeSha() == null ? "" : row.getMergeSha(),
                        "ALREADY_MERGED".equals(row.getStatus()),
                        row.getStatus() == null ? "MERGED" : row.getStatus(),
                        row.getMessage() == null ? "" : row.getMessage()))
                .toList();
    }

    private List<MergeJob> collectMergeJobs(List<String> requirementIds) {
        List<CodingTask> tasks = codingTaskMapper.selectList(new LambdaQueryWrapper<CodingTask>()
                .in(CodingTask::getRequirementId, requirementIds)
                .ne(CodingTask::getStatus, "CANCELLED"));
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        tasks = new ArrayList<>(tasks);
        tasks.sort(Comparator.comparing(task -> task.getCode() == null ? task.getId() : task.getCode()));
        List<String> taskIds = tasks.stream().map(CodingTask::getId).toList();
        List<TaskRepositoryFreeze> freezes = freezeMapper.selectList(
                new LambdaQueryWrapper<TaskRepositoryFreeze>().in(TaskRepositoryFreeze::getTaskId, taskIds));
        if (freezes == null || freezes.isEmpty()) {
            return List.of();
        }
        java.util.Map<String, CodingTask> taskById = tasks.stream()
                .collect(java.util.stream.Collectors.toMap(CodingTask::getId, item -> item, (a, b) -> a));
        List<MergeJob> jobs = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (CodingTask task : tasks) {
            for (TaskRepositoryFreeze freeze : freezes) {
                if (!task.getId().equals(freeze.getTaskId())) {
                    continue;
                }
                if (!"PUSHED".equals(freeze.getDeliveryState()) && !"MR_OPEN".equals(freeze.getDeliveryState())) {
                    continue;
                }
                String source = TaskVerifyDeliveryService.workingBranch(taskById.get(task.getId()), freeze);
                String key = freeze.getProjectRepositoryId() + "|" + source;
                if (seen.add(key)) {
                    jobs.add(new MergeJob(freeze.getProjectRepositoryId(), source));
                }
            }
        }
        return jobs;
    }

    private List<PerformedMerge> mergeForRelease(
            String projectId, String version, List<MergeJob> jobs, List<ReleaseTargetRequest> targets) {
        if (jobs.isEmpty()) {
            return List.of();
        }
        java.util.Map<String, String> targetByRepo = new java.util.LinkedHashMap<>();
        if (targets != null) {
            for (ReleaseTargetRequest target : targets) {
                if (target == null || target.projectRepositoryId() == null || target.projectRepositoryId().isBlank()) {
                    continue;
                }
                if (target.targetBranch() == null || target.targetBranch().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发布必须选择要合并的目标分支");
                }
                targetByRepo.put(target.projectRepositoryId(), target.targetBranch().trim());
            }
        }
        for (MergeJob job : jobs) {
            if (!targetByRepo.containsKey(job.projectRepositoryId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "发布必须为每个待合并仓库选择目标分支，缺少：" + job.projectRepositoryId());
            }
        }
        List<PerformedMerge> performed = new ArrayList<>();
        for (MergeJob job : jobs) {
            String targetBranch = targetByRepo.get(job.projectRepositoryId());
            ProjectRepository repo = projectRepositoryMapper.selectById(job.projectRepositoryId());
            if (repo == null || !projectId.equals(repo.getProjectId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标仓库不属于当前项目");
            }
            try {
                var result = gitHostingPort.mergeBranch(new GitHostingPort.MergeBranch(
                        RepositoryBranchService.toRef(repo),
                        job.sourceBranch(),
                        targetBranch,
                        "ForgePilot 发布 " + version + "：合并 " + job.sourceBranch() + " 到 " + targetBranch));
                performed.add(new PerformedMerge(
                        job.projectRepositoryId(),
                        job.sourceBranch(),
                        targetBranch,
                        result.mergeSha() == null ? "" : result.mergeSha(),
                        result.alreadyMerged(),
                        result.alreadyMerged() ? "ALREADY_MERGED" : "MERGED",
                        result.message() == null ? "" : result.message()));
            } catch (RuntimeException ex) {
                String succeeded = performed.isEmpty()
                        ? "无"
                        : performed.stream()
                                .map(item -> item.sourceBranch() + " → " + item.targetBranch())
                                .reduce((a, b) -> a + "；" + b)
                                .orElse("无");
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "发布合并失败，需求尚未标记为已发布。已成功合并："
                                + succeeded
                                + "。失败："
                                + job.sourceBranch()
                                + " → "
                                + targetBranch
                                + "（"
                                + message
                                + "）");
            }
        }
        return performed;
    }


    private String nextDefectCode(String projectCode) {
        String prefix = "DEF-" + projectCode.toUpperCase() + "-";
        long count = defectMapper.selectCount(new LambdaQueryWrapper<Defect>().likeRight(Defect::getCode, prefix));
        return prefix + (count + 1);
    }

    private Defect requireLinkedDefect(CodingTask task) {
        if (task.getDefectId() == null || task.getDefectId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BUGFIX 任务必须关联缺陷");
        }
        Defect defect = defectMapper.selectById(task.getDefectId());
        if (defect == null || !task.getRequirementId().equals(defect.getRequirementId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺陷不存在或不属于当前需求");
        }
        return defect;
    }

    private Requirement requireMemberRequirement(AuthUser user, String requirementId) {
        Requirement requirement = requireRequirement(requirementId);
        projectAccess.requireProjectMember(user, requirement.getProjectId());
        return requirement;
    }

    private Requirement requireRequirement(String requirementId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "需求不存在或无权查看");
        }
        return requirement;
    }

    private void record(
            AuthUser user,
            String action,
            String targetType,
            String targetId,
            Requirement requirement,
            String summary,
            Map<String, Object> payload) {
        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                action,
                targetType,
                targetId,
                requirement.getProjectId(),
                requirement.getId(),
                null,
                null,
                summary,
                payload,
                "SUCCESS");
    }

    private ReviewView toReviewView(Review review) {
        if (review == null) {
            return null;
        }
        return new ReviewView(
                review.getId(),
                review.getSubjectType(),
                review.getSubjectId(),
                review.getDecision(),
                review.getComment() == null ? "" : review.getComment(),
                review.getReviewerId(),
                review.getCreatedAt());
    }

    private DocumentView toDocumentView(RequirementDocument document) {
        Review latest = latestReview("DESIGN_DOCUMENT", document.getId());
        return new DocumentView(
                document.getId(),
                document.getKind(),
                document.getTitle(),
                document.getBody() == null ? "" : document.getBody(),
                document.getVersionNo(),
                Boolean.TRUE.equals(document.getCurrentFlag()),
                Boolean.TRUE.equals(document.getPendingReview()),
                document.getCreatedBy(),
                document.getCreatedAt(),
                toReviewView(latest));
    }

    private DefectView toDefectView(Defect defect) {
        return new DefectView(
                defect.getId(),
                defect.getCode(),
                defect.getRequirementId(),
                defect.getProjectId(),
                defect.getTaskId(),
                defect.getTitle(),
                defect.getDescription() == null ? "" : defect.getDescription(),
                defect.getSeverity(),
                defect.getStatus(),
                defect.getCreatedBy(),
                defect.getCreatedAt());
    }

    private static String decisionLabel(String decision) {
        return "APPROVED".equals(decision) ? "通过" : "驳回";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public record ReviewView(
            String id,
            String subjectType,
            String subjectId,
            String decision,
            String comment,
            String reviewerId,
            Instant createdAt) {}

    public record DocumentView(
            String id,
            String kind,
            String title,
            String body,
            int versionNo,
            boolean current,
            boolean pendingReview,
            String createdBy,
            Instant createdAt,
            ReviewView latestReview) {}

    public record DefectView(
            String id,
            String code,
            String requirementId,
            String projectId,
            String taskId,
            String title,
            String description,
            String severity,
            String status,
            String createdBy,
            Instant createdAt) {}

    public record ReleaseView(
            String id,
            String projectId,
            String version,
            String notes,
            String releasedBy,
            Instant releasedAt,
            List<String> requirementIds,
            List<String> requirementCodes,
            List<ReleaseTargetView> targets) {}

    public record ReleaseTargetView(
            String projectRepositoryId,
            String sourceBranch,
            String targetBranch,
            String mergeSha,
            String status,
            String message) {}

    public record ReleaseRepoPlan(
            String projectRepositoryId,
            String displayName,
            String localDirectoryName,
            String cloneUrl,
            String defaultTargetBranch,
            List<String> sourceBranches,
            List<String> remoteBranches,
            String branchesError) {}

    public record ReleasePlanView(List<ReleaseRepoPlan> repositories) {}

    private record MergeJob(String projectRepositoryId, String sourceBranch) {}

    private record PerformedMerge(
            String projectRepositoryId,
            String sourceBranch,
            String targetBranch,
            String mergeSha,
            boolean alreadyMerged,
            String status,
            String message) {}

    public record GateView(
            boolean requirementReviewApproved,
            boolean designDocumentExists,
            boolean designReviewApproved,
            boolean codingToolBound,
            boolean modelBound,
            boolean repoBound,
            boolean canStartImplementOrBugfix,
            List<String> blockers,
            int currentStageIndex,
            String currentStage,
            ReviewView latestRequirementReview,
            ReviewView latestDesignReview) {}

    public record LifecycleBundle(
            List<ReviewView> requirementReviews,
            List<DocumentView> documents,
            List<ReviewView> designReviews,
            List<DefectView> defects,
            List<ReleaseView> releases,
            GateView gates) {}

    public record RequirementViewStatus(String id, String status, boolean statusLocked) {}
}
