package com.forgepilot.system.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forgepilot.system.auth.AuthService;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.auth.LoginRequest;
import com.forgepilot.system.auth.LoginResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService)).build();

    @Test
    void loginReturnsToken() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(new LoginResponse(
                "token-1",
                "Bearer",
                new AuthUser("u1", "demo_member", "演示成员", "t1", List.of("MEMBER"), List.of("dashboard"))));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo_member\",\"password\":\"ForgePilot!dev\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-1"))
                .andExpect(jsonPath("$.user.username").value("demo_member"));
    }

    @Test
    void loginFailureReturnsUnauthorized() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo_member\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized());
    }
}
