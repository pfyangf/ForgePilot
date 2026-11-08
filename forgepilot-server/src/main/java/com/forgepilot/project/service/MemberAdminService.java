package com.forgepilot.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.ProjectMember;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.domain.Role;
import com.forgepilot.system.domain.User;
import com.forgepilot.system.persistence.RoleMapper;
import com.forgepilot.system.persistence.UserMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 租户用户与项目成员管理。普通用户只属于一个公司；平台管理员不挂租户、不因管理身份进入项目工作台。
 */
@Service
public class MemberAdminService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectAccess projectAccess;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogRecorder activityLogRecorder;

    public MemberAdminService(
            UserMapper userMapper,
            RoleMapper roleMapper,
            ProjectMemberMapper projectMemberMapper,
            ProjectAccess projectAccess,
            PasswordEncoder passwordEncoder,
            ActivityLogRecorder activityLogRecorder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.projectAccess = projectAccess;
        this.passwordEncoder = passwordEncoder;
        this.activityLogRecorder = activityLogRecorder;
    }

    public List<TenantUserView> listTenantUsers(AuthUser actor) {
        requireTenantAdmin(actor);
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, actor.tenantId())
                .orderByAsc(User::getUsername));
        List<TenantUserView> out = new ArrayList<>();
        for (User u : users) {
            List<Role> roles = roleMapper.findByUserId(u.getId());
            out.add(new TenantUserView(
                    u.getId(),
                    u.getUsername(),
                    u.getDisplayName(),
                    Boolean.TRUE.equals(u.getEnabled()),
                    roles.stream().map(Role::getCode).toList()));
        }
        return out;
    }

    @Transactional
    public TenantUserView createTenantUser(AuthUser actor, CreateTenantUserRequest request) {
        requireTenantAdmin(actor);
        if (request.username() == null || request.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名必填");
        }
        if (request.password() == null || request.password().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "口令至少 8 位");
        }
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        String roleCode = normalizeTenantRole(request.role());
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setTenantId(actor.tenantId());
        user.setUsername(request.username().trim());
        user.setDisplayName(
                request.displayName() == null || request.displayName().isBlank()
                        ? request.username().trim()
                        : request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        userMapper.insert(user);
        Role role = requireRole(roleCode);
        roleMapper.insertUserRole(user.getId(), role.getId());
        activityLogRecorder.record(
                actor.tenantId(),
                "USER",
                actor.userId(),
                "TENANT_USER_CREATE",
                "USER",
                user.getId(),
                null,
                null,
                null,
                null,
                "创建公司用户：" + user.getUsername(),
                Map.of("username", user.getUsername(), "role", roleCode),
                "SUCCESS");
        return new TenantUserView(user.getId(), user.getUsername(), user.getDisplayName(), true, List.of(roleCode));
    }

    public List<ProjectMemberView> listProjectMembers(AuthUser actor, String projectId) {
        projectAccess.requireProjectAdmin(actor, projectId);
        List<ProjectMember> members = projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId));
        List<ProjectMemberView> out = new ArrayList<>();
        for (ProjectMember m : members) {
            User u = userMapper.selectById(m.getUserId());
            if (u == null) {
                continue;
            }
            // 租户隔离：不得泄露其他公司用户
            if (u.getTenantId() == null || !u.getTenantId().equals(actor.tenantId())) {
                continue;
            }
            out.add(new ProjectMemberView(
                    m.getId(),
                    u.getId(),
                    u.getUsername(),
                    u.getDisplayName(),
                    Boolean.TRUE.equals(m.getProjectAdmin()) ? "project-admin" : "member"));
        }
        return out;
    }

    @Transactional
    public ProjectMemberView inviteToProject(AuthUser actor, String projectId, InviteMemberRequest request) {
        Project project = projectAccess.requireProjectAdmin(actor, projectId);
        boolean asAdmin = "project-admin".equalsIgnoreCase(request.role())
                || "PROJECT_ADMIN".equalsIgnoreCase(request.role());
        User user;
        if (request.userId() != null && !request.userId().isBlank()) {
            user = userMapper.selectById(request.userId());
            if (user == null || !actor.tenantId().equals(user.getTenantId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不在本公司");
            }
        } else if (request.username() != null && !request.username().isBlank()) {
            user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username().trim()));
            if (user == null) {
                if (request.password() == null || request.password().length() < 8) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新建用户需要至少 8 位口令");
                }
                // 同租户新建
                user = new User();
                user.setId(UUID.randomUUID().toString());
                user.setTenantId(actor.tenantId());
                user.setUsername(request.username().trim());
                user.setDisplayName(
                        request.displayName() == null || request.displayName().isBlank()
                                ? request.username().trim()
                                : request.displayName().trim());
                user.setPasswordHash(passwordEncoder.encode(request.password()));
                user.setEnabled(true);
                userMapper.insert(user);
                Role role = requireRole(asAdmin ? "PROJECT_ADMIN" : "MEMBER");
                roleMapper.insertUserRole(user.getId(), role.getId());
            } else if (!actor.tenantId().equals(user.getTenantId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能邀请其他公司的用户");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "需要 userId 或 username");
        }

        ProjectMember existing = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, user.getId()));
        if (existing != null) {
            existing.setProjectAdmin(asAdmin);
            existing.setUpdatedBy(actor.userId());
            projectMemberMapper.updateById(existing);
            activityLogRecorder.record(
                    actor.tenantId(),
                    "USER",
                    actor.userId(),
                    "PROJECT_MEMBER_UPDATE",
                    "PROJECT",
                    projectId,
                    projectId,
                    null,
                    null,
                    null,
                    "更新项目成员角色：" + user.getUsername(),
                    Map.of("userId", user.getId(), "role", asAdmin ? "project-admin" : "member"),
                    "SUCCESS");
            return new ProjectMemberView(
                    existing.getId(),
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    asAdmin ? "project-admin" : "member");
        }

        ProjectMember member = new ProjectMember();
        member.setId(UUID.randomUUID().toString());
        member.setProjectId(project.getId());
        member.setUserId(user.getId());
        member.setProjectAdmin(asAdmin);
        member.setCreatedBy(actor.userId());
        projectMemberMapper.insert(member);
        activityLogRecorder.record(
                actor.tenantId(),
                "USER",
                actor.userId(),
                "PROJECT_MEMBER_INVITE",
                "PROJECT",
                projectId,
                projectId,
                null,
                null,
                null,
                "邀请项目成员：" + user.getUsername(),
                Map.of("userId", user.getId(), "role", asAdmin ? "project-admin" : "member"),
                "SUCCESS");
        return new ProjectMemberView(
                member.getId(),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                asAdmin ? "project-admin" : "member");
    }

    @Transactional
    public void removeProjectMember(AuthUser actor, String projectId, String userId) {
        projectAccess.requireProjectAdmin(actor, projectId);
        if (actor.userId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能移除自己");
        }
        User user = userMapper.selectById(userId);
        if (user == null || !actor.tenantId().equals(user.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "成员不存在或不在本公司");
        }
        ProjectMember existing = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "不是项目成员");
        }
        projectMemberMapper.deleteById(existing.getId());
        activityLogRecorder.record(
                actor.tenantId(),
                "USER",
                actor.userId(),
                "PROJECT_MEMBER_REMOVE",
                "PROJECT",
                projectId,
                projectId,
                null,
                null,
                null,
                "移除项目成员：" + user.getUsername(),
                Map.of("userId", userId),
                "SUCCESS");
    }

    private void requireTenantAdmin(AuthUser actor) {
        projectAccess.requireTenant(actor);
        if (!projectAccess.isTenantAdmin(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要租户管理员");
        }
        if (actor.roles() != null && actor.roles().contains("PLATFORM_ADMIN") && actor.tenantId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "平台管理员不挂租户");
        }
    }

    private static String normalizeTenantRole(String role) {
        if (role == null || role.isBlank() || "member".equalsIgnoreCase(role) || "MEMBER".equalsIgnoreCase(role)) {
            return "MEMBER";
        }
        if ("tenant-admin".equalsIgnoreCase(role) || "TENANT_ADMIN".equalsIgnoreCase(role)) {
            return "TENANT_ADMIN";
        }
        if ("project-admin".equalsIgnoreCase(role) || "PROJECT_ADMIN".equalsIgnoreCase(role)) {
            return "PROJECT_ADMIN";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的角色: " + role);
    }

    private Role requireRole(String code) {
        Role role = roleMapper.findByCode(code);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "角色未种子化: " + code);
        }
        return role;
    }

    public record TenantUserView(
            String id, String username, String displayName, boolean enabled, List<String> roles) {
    }

    public record ProjectMemberView(
            String membershipId, String userId, String username, String displayName, String role) {
    }

    public record CreateTenantUserRequest(
            String username, String displayName, String password, String role) {
    }

    public record InviteMemberRequest(
            String userId, String username, String displayName, String password, String role) {
    }
}
