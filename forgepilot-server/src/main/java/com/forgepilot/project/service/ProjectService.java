package com.forgepilot.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.project.domain.LocalDirectoryName;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.ProjectMember;
import com.forgepilot.project.domain.ProjectRepository;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.project.persistence.ProjectRepositoryMapper;
import com.forgepilot.project.web.AddRepositoryRequest;
import com.forgepilot.project.web.CreateProjectRequest;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectRepositoryMapper projectRepositoryMapper;
    private final ProjectAccess projectAccess;
    private final ActivityLogRecorder activityLogRecorder;

    public ProjectService(
            ProjectMapper projectMapper,
            ProjectMemberMapper projectMemberMapper,
            ProjectRepositoryMapper projectRepositoryMapper,
            ProjectAccess projectAccess,
            ActivityLogRecorder activityLogRecorder) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.projectRepositoryMapper = projectRepositoryMapper;
        this.projectAccess = projectAccess;
        this.activityLogRecorder = activityLogRecorder;
    }

    public List<ProjectView> list(AuthUser user) {
        return projectAccess.listVisibleProjects(user).stream().map(this::toView).toList();
    }

    public ProjectDetailView get(AuthUser user, String projectId) {
        Project project = projectAccess.requireProjectMember(user, projectId);
        List<RepositoryView> repos = listRepositoriesInternal(projectId);
        return new ProjectDetailView(toView(project), repos);
    }

    @Transactional
    public ProjectView create(AuthUser user, CreateProjectRequest request) {
        projectAccess.requireTenant(user);
        if (!projectAccess.isTenantAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有租户管理员可以创建项目");
        }
        Project existing = projectMapper.selectOne(new LambdaQueryWrapper<Project>().eq(Project::getCode, request.code()));
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "项目编码已存在");
        }
        Project project = new Project();
        project.setId(UUID.randomUUID().toString());
        project.setTenantId(user.tenantId());
        project.setName(request.name().trim());
        project.setCode(request.code().trim());
        project.setDescription(blankToNull(request.description()));
        project.setTechStack(blankToNull(request.techStack()));
        project.setStatus("ACTIVE");
        project.setFeedbackLimit(2);
        project.setCreatedBy(user.userId());
        project.setUpdatedBy(user.userId());
        try {
            projectMapper.insert(project);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "项目编码已存在");
        }

        ProjectMember member = new ProjectMember();
        member.setId(UUID.randomUUID().toString());
        member.setProjectId(project.getId());
        member.setUserId(user.userId());
        member.setProjectAdmin(true);
        member.setCreatedBy(user.userId());
        projectMemberMapper.insert(member);

        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "PROJECT_CREATE",
                "PROJECT",
                project.getId(),
                project.getId(),
                null,
                null,
                null,
                "创建项目：" + project.getName(),
                Map.of("code", project.getCode(), "name", project.getName()),
                "SUCCESS");
        return toView(project);
    }

    public List<RepositoryView> listRepositories(AuthUser user, String projectId) {
        projectAccess.requireProjectMember(user, projectId);
        return listRepositoriesInternal(projectId);
    }

    @Transactional
    public RepositoryView addRepository(AuthUser user, String projectId, AddRepositoryRequest request) {
        Project project = projectAccess.requireProjectAdmin(user, projectId);
        if ("ARCHIVED".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "归档项目不能再关联仓库");
        }
        String directory;
        try {
            directory = LocalDirectoryName.require(request.localDirectoryName().trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        String cloneUrl = request.cloneUrl().trim();
        if (cloneUrl.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库地址不能为空");
        }
        Long dirDup = projectRepositoryMapper.selectCount(new LambdaQueryWrapper<ProjectRepository>()
                .eq(ProjectRepository::getProjectId, projectId)
                .eq(ProjectRepository::getLocalDirectoryName, directory));
        if (dirDup != null && dirDup > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "本地检出目录名在项目内必须唯一");
        }
        Long urlDup = projectRepositoryMapper.selectCount(new LambdaQueryWrapper<ProjectRepository>()
                .eq(ProjectRepository::getProjectId, projectId)
                .eq(ProjectRepository::getCloneUrl, cloneUrl));
        if (urlDup != null && urlDup > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同一项目不能重复关联相同仓库地址");
        }

        String provider = request.providerType() == null || request.providerType().isBlank()
                ? "GITLAB"
                : request.providerType().trim().toUpperCase();
        if (!"GITLAB".equals(provider) && !"GITHUB".equals(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerType 仅支持 GITLAB 或 GITHUB");
        }
        // MVP 只交付 GitLab 适配器；仍允许登记字段，不在此处伪造连通性。
        String branch = blankToNull(request.defaultBranch());
        if (branch == null) {
            branch = "main";
        }

        ProjectRepository repo = new ProjectRepository();
        repo.setId(UUID.randomUUID().toString());
        repo.setProjectId(projectId);
        repo.setProviderType(provider);
        repo.setDisplayName(request.displayName().trim());
        repo.setCloneUrl(cloneUrl);
        repo.setLocalDirectoryName(directory);
        repo.setDefaultBranch(branch);
        repo.setCreatedBy(user.userId());
        try {
            projectRepositoryMapper.insert(repo);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仓库连接与现有记录冲突");
        }

        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "PROJECT_REPOSITORY_ADD",
                "PROJECT_REPOSITORY",
                repo.getId(),
                projectId,
                null,
                null,
                null,
                "关联仓库：" + repo.getDisplayName(),
                Map.of(
                        "displayName", repo.getDisplayName(),
                        "cloneUrl", repo.getCloneUrl(),
                        "localDirectoryName", repo.getLocalDirectoryName(),
                        "defaultBranch", repo.getDefaultBranch(),
                        "providerType", repo.getProviderType()),
                "SUCCESS");
        return toRepoView(repo);
    }

    private List<RepositoryView> listRepositoriesInternal(String projectId) {
        return projectRepositoryMapper
                .selectList(new LambdaQueryWrapper<ProjectRepository>()
                        .eq(ProjectRepository::getProjectId, projectId)
                        .orderByAsc(ProjectRepository::getLocalDirectoryName))
                .stream()
                .map(this::toRepoView)
                .toList();
    }

    private ProjectView toView(Project project) {
        return new ProjectView(
                project.getId(),
                project.getTenantId(),
                project.getName(),
                project.getCode(),
                project.getDescription(),
                project.getTechStack(),
                project.getStatus(),
                project.getFeedbackLimit(),
                project.getDefaultAgentProfileId(),
                project.getDefaultModelProviderId(),
                project.getDefaultModelId());
    }

    @Transactional
    public ProjectView updateDefaults(AuthUser user, String projectId, UpdateDefaultsRequest request) {
        Project project = projectAccess.requireProjectAdmin(user, projectId);
        if (request.defaultAgentProfileId() != null) {
            project.setDefaultAgentProfileId(blankToNull(request.defaultAgentProfileId()));
        }
        if (request.defaultModelProviderId() != null) {
            project.setDefaultModelProviderId(blankToNull(request.defaultModelProviderId()));
        }
        if (request.defaultModelId() != null) {
            project.setDefaultModelId(blankToNull(request.defaultModelId()));
        }
        project.setUpdatedBy(user.userId());
        projectMapper.updateById(project);
        activityLogRecorder.record(
                user.tenantId(),
                "USER",
                user.userId(),
                "PROJECT_DEFAULTS_UPDATE",
                "PROJECT",
                projectId,
                projectId,
                null,
                null,
                null,
                "更新项目默认工具与模型",
                Map.of(
                        "defaultAgentProfileId",
                        project.getDefaultAgentProfileId() == null ? "" : project.getDefaultAgentProfileId(),
                        "defaultModelProviderId",
                        project.getDefaultModelProviderId() == null ? "" : project.getDefaultModelProviderId(),
                        "defaultModelId",
                        project.getDefaultModelId() == null ? "" : project.getDefaultModelId()),
                "SUCCESS");
        return toView(project);
    }

    public record UpdateDefaultsRequest(
            String defaultAgentProfileId, String defaultModelProviderId, String defaultModelId) {
    }

    private RepositoryView toRepoView(ProjectRepository repo) {
        return new RepositoryView(
                repo.getId(),
                repo.getProjectId(),
                repo.getProviderType(),
                repo.getDisplayName(),
                repo.getCloneUrl(),
                repo.getLocalDirectoryName(),
                repo.getDefaultBranch());
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ProjectView(
            String id,
            String tenantId,
            String name,
            String code,
            String description,
            String techStack,
            String status,
            Integer feedbackLimit,
            String defaultAgentProfileId,
            String defaultModelProviderId,
            String defaultModelId) {
    }

    public record RepositoryView(
            String id,
            String projectId,
            String providerType,
            String displayName,
            String cloneUrl,
            String localDirectoryName,
            String defaultBranch) {
    }

    public record ProjectDetailView(ProjectView project, List<RepositoryView> repositories) {
    }
}
