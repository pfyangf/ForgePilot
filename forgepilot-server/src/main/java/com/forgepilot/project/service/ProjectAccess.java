package com.forgepilot.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.ProjectMember;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.system.auth.AuthUser;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 租户边界 + 项目成员关系。平台管理员不因管理身份进入项目。 */
@Component
public class ProjectAccess {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;

    public ProjectAccess(ProjectMapper projectMapper, ProjectMemberMapper projectMemberMapper) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
    }

    public void requireTenant(AuthUser user) {
        if (user.tenantId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "平台管理员不挂租户，不能操作公司项目数据");
        }
    }

    public boolean isTenantAdmin(AuthUser user) {
        return user.roles() != null && user.roles().contains("TENANT_ADMIN");
    }

    public Set<String> membershipProjectIds(String userId) {
        return new HashSet<>(projectMemberMapper.findProjectIdsByUserId(userId));
    }

    public Project requireProjectInTenant(AuthUser user, String projectId) {
        requireTenant(user);
        Project project = projectMapper.selectById(projectId);
        if (project == null || !user.tenantId().equals(project.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在或无权查看");
        }
        return project;
    }

    public Project requireProjectMember(AuthUser user, String projectId) {
        Project project = requireProjectInTenant(user, projectId);
        if (isTenantAdmin(user)) {
            return project;
        }
        if (!membershipProjectIds(user.userId()).contains(projectId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不是该项目成员");
        }
        return project;
    }

    public Project requireProjectAdmin(AuthUser user, String projectId) {
        Project project = requireProjectInTenant(user, projectId);
        if (isTenantAdmin(user)) {
            return project;
        }
        ProjectMember member = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, user.userId()));
        if (member == null || !Boolean.TRUE.equals(member.getProjectAdmin())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要项目管理员权限");
        }
        return project;
    }

    public List<Project> listVisibleProjects(AuthUser user) {
        requireTenant(user);
        if (isTenantAdmin(user)) {
            return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                    .eq(Project::getTenantId, user.tenantId())
                    .orderByAsc(Project::getName));
        }
        Set<String> ids = membershipProjectIds(user.userId());
        if (ids.isEmpty()) {
            return List.of();
        }
        return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getTenantId, user.tenantId())
                .in(Project::getId, ids)
                .orderByAsc(Project::getName));
    }
}
