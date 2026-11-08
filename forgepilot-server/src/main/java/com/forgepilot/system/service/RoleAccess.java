package com.forgepilot.system.service;

import com.forgepilot.system.auth.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RoleAccess {

    public boolean isPlatformAdmin(AuthUser user) {
        return user.roles() != null && user.roles().contains("PLATFORM_ADMIN");
    }

    public boolean isTenantAdmin(AuthUser user) {
        return user.roles() != null && user.roles().contains("TENANT_ADMIN");
    }

    public void requirePlatformAdmin(AuthUser user) {
        if (!isPlatformAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要平台管理员");
        }
    }

    public void requireTenantAdmin(AuthUser user) {
        if (user.tenantId() == null || !isTenantAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要租户管理员");
        }
    }
}
