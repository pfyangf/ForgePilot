package com.forgepilot.system.auth;

import java.util.List;

public record AuthUser(
        String userId,
        String username,
        String displayName,
        String tenantId,
        List<String> roles,
        List<String> menus) {
}
