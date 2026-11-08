package com.forgepilot.system.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.domain.Role;
import com.forgepilot.system.domain.User;
import com.forgepilot.system.persistence.RoleMapper;
import com.forgepilot.system.persistence.UserMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ActivityLogRecorder activityLogRecorder;
    private final MenuCatalog menuCatalog;

    public AuthService(
            UserMapper userMapper,
            RoleMapper roleMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            ActivityLogRecorder activityLogRecorder,
            MenuCatalog menuCatalog) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.activityLogRecorder = activityLogRecorder;
        this.menuCatalog = menuCatalog;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username()));
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            activityLogRecorder.record(
                    null,
                    "SYSTEM",
                    null,
                    "AUTH_LOGIN_FAILED",
                    "USER",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "登录失败：" + request.username(),
                    Map.of("username", request.username()),
                    "FAILURE");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        AuthUser authUser = toAuthUser(user);
        String token = jwtService.issue(authUser);
        activityLogRecorder.record(
                user.getTenantId(),
                "USER",
                user.getId(),
                "AUTH_LOGIN",
                "USER",
                user.getId(),
                null,
                null,
                null,
                null,
                "用户登录成功：" + user.getUsername(),
                Map.of("username", user.getUsername()),
                "SUCCESS");
        return new LoginResponse(token, "Bearer", authUser);
    }

    public AuthUser toAuthUser(User user) {
        List<Role> roles = roleMapper.findByUserId(user.getId());
        List<String> roleCodes = roles.stream().map(Role::getCode).toList();
        return new AuthUser(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getTenantId(),
                roleCodes,
                menuCatalog.menusFor(roleCodes));
    }

    public AuthUser requireUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不可用");
        }
        return toAuthUser(user);
    }
}
