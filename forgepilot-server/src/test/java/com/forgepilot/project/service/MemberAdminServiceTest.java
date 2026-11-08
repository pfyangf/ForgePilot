package com.forgepilot.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.ProjectMember;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.domain.Role;
import com.forgepilot.system.domain.User;
import com.forgepilot.system.persistence.RoleMapper;
import com.forgepilot.system.persistence.UserMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class MemberAdminServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final ProjectMemberMapper memberMapper = mock(ProjectMemberMapper.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final ActivityLogRecorder recorder = mock(ActivityLogRecorder.class);
    private final List<User> users = new ArrayList<>();
    private final List<ProjectMember> members = new ArrayList<>();
    private MemberAdminService service;

    @BeforeEach
    void setUp() {
        ProjectAccess access = new ProjectAccess(projectMapper, memberMapper);
        service = new MemberAdminService(
                userMapper, roleMapper, memberMapper, access, new BCryptPasswordEncoder(), recorder);

        Project project = new Project();
        project.setId("p1");
        project.setTenantId("t1");
        when(projectMapper.selectById("p1")).thenReturn(project);

        Project other = new Project();
        other.setId("p-other");
        other.setTenantId("t2");
        when(projectMapper.selectById("p-other")).thenReturn(other);

        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            users.add(inv.getArgument(0));
            return 1;
        });
        when(userMapper.selectById(any())).thenAnswer(inv -> users.stream()
                .filter(u -> u.getId().equals(inv.getArgument(0)))
                .findFirst()
                .orElse(null));
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(inv -> users.isEmpty() ? null : users.get(0));
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> List.copyOf(users));

        when(memberMapper.insert(any(ProjectMember.class))).thenAnswer(inv -> {
            members.add(inv.getArgument(0));
            return 1;
        });
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(inv -> members.isEmpty() ? null : members.get(0));
        when(memberMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> List.copyOf(members));
        when(memberMapper.findProjectIdsByUserId(any())).thenAnswer(inv -> members.stream()
                .filter(m -> m.getUserId().equals(inv.getArgument(0)))
                .map(ProjectMember::getProjectId)
                .toList());
        when(memberMapper.deleteById(any(String.class))).thenAnswer(inv -> {
            members.removeIf(m -> m.getId().equals(inv.getArgument(0)));
            return 1;
        });

        Role memberRole = new Role();
        memberRole.setId("role-member");
        memberRole.setCode("MEMBER");
        when(roleMapper.findByCode("MEMBER")).thenReturn(memberRole);
        when(roleMapper.findByCode("PROJECT_ADMIN")).thenAnswer(inv -> {
            Role r = new Role();
            r.setId("role-pa");
            r.setCode("PROJECT_ADMIN");
            return r;
        });
        when(roleMapper.insertUserRole(any(), any())).thenReturn(1);
        when(roleMapper.findByUserId(any())).thenReturn(List.of(memberRole));
    }

    @Test
    void tenantAdminInvitesMemberAndRejectsCrossTenant() {
        AuthUser admin = new AuthUser("admin", "demo_admin", "Admin", "t1", List.of("TENANT_ADMIN"), List.of());
        var created = service.createTenantUser(
                admin, new MemberAdminService.CreateTenantUserRequest("new_user", "New", "ForgePilot!dev", "member"));
        assertThat(created.username()).isEqualTo("new_user");
        assertThat(users.get(0).getTenantId()).isEqualTo("t1");

        var invited = service.inviteToProject(
                admin,
                "p1",
                new MemberAdminService.InviteMemberRequest(created.id(), null, null, null, "member"));
        assertThat(invited.role()).isEqualTo("member");
        assertThat(members).hasSize(1);

        // 跨租户项目
        assertThatThrownBy(() -> service.listProjectMembers(admin, "p-other"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void platformAdminWithoutTenantCannotListUsers() {
        AuthUser platform = new AuthUser("plat", "platform_admin", "P", null, List.of("PLATFORM_ADMIN"), List.of());
        assertThatThrownBy(() -> service.listTenantUsers(platform))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void cannotInviteOtherCompanyUserByUsername() {
        AuthUser admin = new AuthUser("admin", "demo_admin", "Admin", "t1", List.of("TENANT_ADMIN"), List.of());
        User foreign = new User();
        foreign.setId("u-foreign");
        foreign.setTenantId("t2");
        foreign.setUsername("other_member");
        foreign.setDisplayName("Other");
        foreign.setEnabled(true);
        users.add(foreign);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(foreign);

        assertThatThrownBy(() -> service.inviteToProject(
                        admin,
                        "p1",
                        new MemberAdminService.InviteMemberRequest(null, "other_member", null, null, "member")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("其他公司");
    }
}
