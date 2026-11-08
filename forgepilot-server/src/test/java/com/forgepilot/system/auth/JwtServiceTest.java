package com.forgepilot.system.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void issuesAndParsesToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-32-bytes!!");
        JwtService jwtService = new JwtService(properties);
        AuthUser user = new AuthUser(
                "u1", "demo_member", "演示成员", "t1", List.of("MEMBER"), List.of("dashboard"));

        String token = jwtService.issue(user);
        AuthUser parsed = jwtService.parse(token);

        assertThat(parsed.userId()).isEqualTo("u1");
        assertThat(parsed.tenantId()).isEqualTo("t1");
        assertThat(parsed.roles()).containsExactly("MEMBER");
        assertThat(parsed.menus()).containsExactly("dashboard");
    }

    @Test
    void rejectsTamperedToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-32-bytes!!");
        JwtService jwtService = new JwtService(properties);
        String token = jwtService.issue(new AuthUser("u1", "a", "A", "t1", List.of(), List.of()));
        assertThatThrownBy(() -> jwtService.parse(token + "x")).isInstanceOf(RuntimeException.class);
    }
}
