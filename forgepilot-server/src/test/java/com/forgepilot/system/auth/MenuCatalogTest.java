package com.forgepilot.system.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MenuCatalogTest {

    private final MenuCatalog catalog = new MenuCatalog();

    @Test
    void platformAdminGetsInstanceMenusIncludingNodes() {
        assertThat(catalog.menusFor(List.of("PLATFORM_ADMIN")))
                .containsExactly("admin", "nodes", "platform-guidance", "activity");
    }

    @Test
    void tenantAdminGetsCompanyMenusIncludingCatalog() {
        assertThat(catalog.menusFor(List.of("TENANT_ADMIN")))
                .containsExactly("dashboard", "projects", "catalog", "tenant-guidance", "activity");
    }

    @Test
    void projectAdminAndMemberDoNotGetPlatformMenus() {
        assertThat(catalog.menusFor(List.of("PROJECT_ADMIN")))
                .containsExactly("dashboard", "projects", "ai-coding", "delivery", "project-guidance", "activity")
                .doesNotContain("admin", "nodes", "catalog", "platform-guidance");
        assertThat(catalog.menusFor(List.of("MEMBER")))
                .containsExactly("dashboard", "projects", "ai-coding", "delivery", "activity")
                .doesNotContain("admin", "nodes", "catalog");
    }
}
