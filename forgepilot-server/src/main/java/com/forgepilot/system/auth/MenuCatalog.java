package com.forgepilot.system.auth;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * MVP 菜单权限按角色固定。与 V2 种子中的 sys_menu_permission 对齐。
 */
@Component
public class MenuCatalog {

    private static final Map<String, List<String>> ROLE_MENUS = Map.of(
            "PLATFORM_ADMIN", List.of("admin", "platform-guidance", "activity"),
            "TENANT_ADMIN", List.of("dashboard", "projects", "catalog", "tenant-guidance", "activity"),
            "PROJECT_ADMIN", List.of("dashboard", "projects", "ai-coding", "delivery", "project-guidance", "activity"),
            "MEMBER", List.of("dashboard", "projects", "ai-coding", "delivery", "activity"));

    public List<String> menusFor(List<String> roleCodes) {
        Set<String> menus = new LinkedHashSet<>();
        for (String role : roleCodes) {
            menus.addAll(ROLE_MENUS.getOrDefault(role, List.of()));
        }
        return List.copyOf(menus);
    }
}
