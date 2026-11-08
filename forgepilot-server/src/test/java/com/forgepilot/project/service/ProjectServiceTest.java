package com.forgepilot.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.ProjectRepository;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.project.persistence.ProjectRepositoryMapper;
import com.forgepilot.project.web.AddRepositoryRequest;
import com.forgepilot.project.web.CreateProjectRequest;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class ProjectServiceTest {

    private static final String TENANT = "t1";
    private static final String ADMIN = "u-admin";
    private static final String MEMBER = "u-member";
    private static final String PROJECT = "p1";

    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final ProjectMemberMapper projectMemberMapper = mock(ProjectMemberMapper.class);
    private final ProjectRepositoryMapper projectRepositoryMapper = mock(ProjectRepositoryMapper.class);
    private final ActivityLogRecorder activityLogRecorder = mock(ActivityLogRecorder.class);
    private ProjectAccess projectAccess;
    private ProjectService service;

    @BeforeEach
    void setUp() {
        projectAccess = new ProjectAccess(projectMapper, projectMemberMapper);
        service = new ProjectService(
                projectMapper, projectMemberMapper, projectRepositoryMapper, projectAccess, activityLogRecorder);
    }

    @Test
    void tenantAdminCanCreateProjectAndBecomeProjectAdmin() {
        AuthUser admin = user(ADMIN, TENANT, "TENANT_ADMIN");
        when(projectMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(projectMapper.insert(any(Project.class))).thenReturn(1);
        when(projectMemberMapper.insert(any(com.forgepilot.project.domain.ProjectMember.class))).thenReturn(1);

        ProjectService.ProjectView view =
                service.create(admin, new CreateProjectRequest("支付", "pay2", "desc", "Java"));

        assertThat(view.name()).isEqualTo("支付");
        assertThat(view.code()).isEqualTo("pay2");
        assertThat(view.tenantId()).isEqualTo(TENANT);
        ArgumentCaptor<com.forgepilot.project.domain.ProjectMember> member =
                ArgumentCaptor.forClass(com.forgepilot.project.domain.ProjectMember.class);
        verify(projectMemberMapper).insert(member.capture());
        assertThat(member.getValue().getProjectAdmin()).isTrue();
        verify(activityLogRecorder)
                .record(eq(TENANT), eq("USER"), eq(ADMIN), eq("PROJECT_CREATE"), eq("PROJECT"), any(), any(), any(), any(), any(), any(), any(), eq("SUCCESS"));
    }

    @Test
    void memberCannotCreateProject() {
        AuthUser member = user(MEMBER, TENANT, "MEMBER");
        assertThatThrownBy(() -> service.create(member, new CreateProjectRequest("x", "x1", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("租户管理员");
    }

    @Test
    void otherTenantCannotSeeProject() {
        AuthUser other = user("other", "t2", "MEMBER");
        when(projectMemberMapper.findProjectIdsByUserId("other")).thenReturn(List.of("foreign"));
        Project foreign = project("foreign", "t2");
        when(projectMapper.selectById(PROJECT)).thenReturn(project(PROJECT, TENANT));
        assertThatThrownBy(() -> service.get(other, PROJECT)).isInstanceOf(ResponseStatusException.class);
        assertThat(foreign.getTenantId()).isEqualTo("t2");
    }

    @Test
    void projectAdminCanAssociateRepositoriesWithUniqueDirectory() {
        AuthUser admin = user(ADMIN, TENANT, "TENANT_ADMIN");
        when(projectMapper.selectById(PROJECT)).thenReturn(project(PROJECT, TENANT));
        when(projectRepositoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(projectRepositoryMapper.insert(any(ProjectRepository.class))).thenReturn(1);

        ProjectService.RepositoryView repo = service.addRepository(
                admin,
                PROJECT,
                new AddRepositoryRequest("api", "https://gitlab.example.com/a.git", "api", "main", "GITLAB"));

        assertThat(repo.localDirectoryName()).isEqualTo("api");
        assertThat(repo.defaultBranch()).isEqualTo("main");
        verify(activityLogRecorder)
                .record(eq(TENANT), eq("USER"), eq(ADMIN), eq("PROJECT_REPOSITORY_ADD"), eq("PROJECT_REPOSITORY"), any(), eq(PROJECT), any(), any(), any(), any(), any(), eq("SUCCESS"));
    }

    @Test
    void rejectsUnsafeLocalDirectoryName() {
        AuthUser admin = user(ADMIN, TENANT, "TENANT_ADMIN");
        when(projectMapper.selectById(PROJECT)).thenReturn(project(PROJECT, TENANT));
        assertThatThrownBy(() -> service.addRepository(
                        admin,
                        PROJECT,
                        new AddRepositoryRequest("api", "https://x/a.git", "../etc", "main", "GITLAB")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("检出目录名");
    }

    private static AuthUser user(String id, String tenant, String role) {
        return new AuthUser(id, id, id, tenant, List.of(role), List.of("projects"));
    }

    private static Project project(String id, String tenant) {
        Project project = new Project();
        project.setId(id);
        project.setTenantId(tenant);
        project.setName(id);
        project.setCode(id);
        project.setStatus("ACTIVE");
        return project;
    }
}
