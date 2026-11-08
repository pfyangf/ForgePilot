package com.forgepilot.system.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.domain.Role;
import com.forgepilot.system.domain.User;
import com.forgepilot.system.persistence.RoleMapper;
import com.forgepilot.system.persistence.UserMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final ActivityLogRecorder activityLogRecorder = mock(ActivityLogRecorder.class);
    private final JwtProperties jwtProperties = new JwtProperties();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtProperties.setSecret("unit-test-secret-key-32-bytes!!");
        authService = new AuthService(
                userMapper,
                roleMapper,
                new BCryptPasswordEncoder(),
                new JwtService(jwtProperties),
                activityLogRecorder,
                new MenuCatalog());
    }

    @Test
    void loginHappyPathIssuesJwtAndWritesAudit() {
        User user = user("20000000-0000-4000-8000-000000000003", "10000000-0000-4000-8000-000000000001", "demo_member");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        Role role = new Role();
        role.setCode("MEMBER");
        when(roleMapper.findByUserId(user.getId())).thenReturn(List.of(role));

        LoginResponse response = authService.login(new LoginRequest("demo_member", "ForgePilot!dev"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().username()).isEqualTo("demo_member");
        assertThat(response.user().roles()).containsExactly("MEMBER");
        assertThat(response.user().menus()).contains("dashboard");
        verify(activityLogRecorder).record(
                eq(user.getTenantId()),
                eq("USER"),
                eq(user.getId()),
                eq("AUTH_LOGIN"),
                eq("USER"),
                eq(user.getId()),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("用户登录成功：demo_member"),
                any(),
                eq("SUCCESS"));
    }

    @Test
    void loginFailsOnBadPassword() {
        User user = user("u1", "t1", "demo_member");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        assertThatThrownBy(() -> authService.login(new LoginRequest("demo_member", "wrong")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
        verify(activityLogRecorder).record(
                isNull(),
                eq("SYSTEM"),
                isNull(),
                eq("AUTH_LOGIN_FAILED"),
                eq("USER"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("登录失败：demo_member"),
                any(),
                eq("FAILURE"));
    }

    private static User user(String id, String tenantId, String username) {
        User user = new User();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setEnabled(true);
        user.setPasswordHash(new BCryptPasswordEncoder().encode("ForgePilot!dev"));
        return user;
    }
}
