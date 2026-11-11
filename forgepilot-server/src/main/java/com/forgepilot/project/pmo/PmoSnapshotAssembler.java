package com.forgepilot.project.pmo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.project.domain.Defect;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.ProjectRepository;
import com.forgepilot.project.domain.Requirement;
import com.forgepilot.project.domain.RequirementDocument;
import com.forgepilot.project.domain.Review;
import com.forgepilot.project.lifecycle.RequirementStatusMachine.GateSnapshot;
import com.forgepilot.project.persistence.DefectMapper;
import com.forgepilot.project.persistence.ProjectRepositoryMapper;
import com.forgepilot.project.persistence.RequirementDocumentMapper;
import com.forgepilot.project.persistence.ReviewMapper;
import com.forgepilot.project.pmo.PmoSnapshot.DefectInfo;
import com.forgepilot.project.pmo.PmoSnapshot.TaskInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 把需求、门禁、任务和缺陷装配成规划器快照，工作台走批量查询。 */
@Component
public class PmoSnapshotAssembler {

    private final ProjectRepositoryMapper projectRepositoryMapper;
    private final RequirementDocumentMapper documentMapper;
    private final ReviewMapper reviewMapper;
    private final DefectMapper defectMapper;
    private final CodingTaskMapper codingTaskMapper;

    public PmoSnapshotAssembler(
            ProjectRepositoryMapper projectRepositoryMapper,
            RequirementDocumentMapper documentMapper,
            ReviewMapper reviewMapper,
            DefectMapper defectMapper,
            CodingTaskMapper codingTaskMapper) {
        this.projectRepositoryMapper = projectRepositoryMapper;
        this.documentMapper = documentMapper;
        this.reviewMapper = reviewMapper;
        this.defectMapper = defectMapper;
        this.codingTaskMapper = codingTaskMapper;
    }

    public PmoSnapshot assemble(Requirement requirement, Project project, List<CodingTask> tasks) {
        List<PmoSnapshot> snapshots = assemble(List.of(requirement), Map.of(project.getId(), project), tasks);
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }

    public List<CodingTask> tasksOf(String requirementId) {
        return codingTaskMapper.selectList(
                new LambdaQueryWrapper<CodingTask>().eq(CodingTask::getRequirementId, requirementId));
    }

    public List<PmoSnapshot> assemble(
            List<Requirement> requirements, Map<String, Project> projectById, List<CodingTask> allTasks) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }
        List<String> requirementIds = requirements.stream().map(Requirement::getId).toList();
        Set<String> projectIds = requirements.stream().map(Requirement::getProjectId).collect(Collectors.toSet());

        Set<String> projectsWithRepo = projectRepositoryMapper
                .selectList(new LambdaQueryWrapper<ProjectRepository>().in(ProjectRepository::getProjectId, projectIds))
                .stream()
                .map(ProjectRepository::getProjectId)
                .collect(Collectors.toSet());

        Map<String, RequirementDocument> currentDocByRequirement = documentMapper
                .selectList(new LambdaQueryWrapper<RequirementDocument>()
                        .in(RequirementDocument::getRequirementId, requirementIds)
                        .eq(RequirementDocument::getKind, "DESIGN")
                        .eq(RequirementDocument::getCurrentFlag, true))
                .stream()
                .collect(Collectors.toMap(RequirementDocument::getRequirementId, doc -> doc, (a, b) -> a));

        Map<String, Review> latestRequirementReview = latestBySubject(reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getSubjectType, "REQUIREMENT")
                        .in(Review::getSubjectId, requirementIds)));

        List<String> documentIds =
                currentDocByRequirement.values().stream().map(RequirementDocument::getId).toList();
        Map<String, Review> latestDesignReview = documentIds.isEmpty()
                ? Map.of()
                : latestBySubject(reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                        .eq(Review::getSubjectType, "DESIGN_DOCUMENT")
                        .in(Review::getSubjectId, documentIds)));

        Map<String, List<Defect>> defectsByRequirement = defectMapper
                .selectList(new LambdaQueryWrapper<Defect>().in(Defect::getRequirementId, requirementIds))
                .stream()
                .collect(Collectors.groupingBy(Defect::getRequirementId));

        Map<String, List<CodingTask>> tasksByRequirement = allTasks == null
                ? Map.of()
                : allTasks.stream()
                        .filter(task -> task.getRequirementId() != null)
                        .collect(Collectors.groupingBy(CodingTask::getRequirementId));

        List<PmoSnapshot> snapshots = new ArrayList<>();
        for (Requirement requirement : requirements) {
            Project project = projectById.get(requirement.getProjectId());
            RequirementDocument current = currentDocByRequirement.get(requirement.getId());
            Review requirementReview = latestRequirementReview.get(requirement.getId());
            Review designReview = current == null ? null : latestDesignReview.get(current.getId());
            boolean codingToolBound = project != null && notBlank(project.getDefaultAgentProfileId());
            boolean modelBound = project != null
                    && notBlank(project.getDefaultModelProviderId())
                    && notBlank(project.getDefaultModelId());
            boolean repoBound = projectsWithRepo.contains(requirement.getProjectId());
            GateSnapshot gates = new GateSnapshot(
                    requirementReview != null && "APPROVED".equals(requirementReview.getDecision()),
                    current != null,
                    designReview != null && "APPROVED".equals(designReview.getDecision()),
                    codingToolBound,
                    modelBound,
                    repoBound);
            List<TaskInfo> tasks = tasksByRequirement.getOrDefault(requirement.getId(), List.of()).stream()
                    .map(task -> new TaskInfo(task.getId(), task.getTaskType(), task.getStatus(), task.getDefectId()))
                    .toList();
            List<DefectInfo> defects = defectsByRequirement.getOrDefault(requirement.getId(), List.of()).stream()
                    .map(defect -> new DefectInfo(defect.getId(), defect.getCode(), defect.getTitle(), defect.getStatus()))
                    .toList();
            snapshots.add(new PmoSnapshot(
                    requirement.getId(),
                    requirement.getCode(),
                    requirement.getTitle(),
                    requirement.getStatus(),
                    requirement.getProjectId(),
                    gates,
                    tasks,
                    defects));
        }
        return snapshots;
    }

    private static Map<String, Review> latestBySubject(List<Review> reviews) {
        Map<String, Review> latest = new HashMap<>();
        for (Review review : reviews) {
            Review current = latest.get(review.getSubjectId());
            if (current == null || isNewer(review, current)) {
                latest.put(review.getSubjectId(), review);
            }
        }
        return latest;
    }

    private static boolean isNewer(Review left, Review right) {
        if (left.getCreatedAt() == null) {
            return false;
        }
        if (right.getCreatedAt() == null) {
            return true;
        }
        int compared = left.getCreatedAt().compareTo(right.getCreatedAt());
        if (compared != 0) {
            return compared > 0;
        }
        return left.getId() != null && (right.getId() == null || left.getId().compareTo(right.getId()) > 0);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
