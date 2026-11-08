package com.forgepilot.system.web;

import com.forgepilot.system.auth.AuthService;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.auth.LoginRequest;
import com.forgepilot.system.auth.LoginResponse;
import com.forgepilot.system.security.AuthContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AuthUser me() {
        return AuthContext.require();
    }
}
