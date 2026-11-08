package com.forgepilot.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.domain.GuidanceItem;
import com.forgepilot.system.persistence.ConventionCompositionItemMapper;
import com.forgepilot.system.persistence.ConventionCompositionMapper;
import com.forgepilot.system.persistence.GuidanceItemMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GuidanceServiceTest {

    private final GuidanceItemMapper guidanceItemMapper = mock(GuidanceItemMapper.class);
    private final ConventionCompositionMapper compositionMapper = mock(ConventionCompositionMapper.class);
    private final ConventionCompositionItemMapper compositionItemMapper = mock(ConventionCompositionItemMapper.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final ProjectMemberMapper projectMemberMapper = mock(ProjectMemberMapper.class);
    private final ActivityLogRecorder recorder = mock(ActivityLogRecorder.class);
    private GuidanceService service;

    @BeforeEach
    void setUp() {
        ProjectAccess access = new ProjectAccess(projectMapper, projectMemberMapper);
        service = new GuidanceService(
                guidanceItemMapper, compositionMapper, compositionItemMapper, access, new RoleAccess(), recorder);
        when(projectMemberMapper.findProjectIdsByUserId("u1")).thenReturn(List.of("p1"));
        Project project = new Project();
        project.setId("p1");
        project.setTenantId("t1");
        project.setName("支付");
        project.setStatus("ACTIVE");
        when(projectMapper.selectById("p1")).thenReturn(project);
        when(compositionMapper.insert(any(com.forgepilot.system.domain.ConventionComposition.class))).thenReturn(1);
        when(compositionItemMapper.insert(any(com.forgepilot.system.domain.ConventionCompositionItem.class))).thenReturn(1);
    }

    @Test
    void composeLetsTenantOverridePlatformSameTitleAndKeepsDifferentTitles() {
        when(guidanceItemMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> List.of(
                item("PLATFORM", null, null, "CONVENTION", "提交说明", "平台要求写明动机", true, true),
                item("TENANT", "t1", null, "CONVENTION", "提交说明", "公司要求带工单号", true, true),
                item("PROJECT", "t1", "p1", "CONVENTION", "模块边界", "web 不直接访问支付库", true, true),
                item("PROJECT", "t1", "p1", "DOCUMENT", "架构笔记", "给人看的长文", true, false)));

        AuthUser user = new AuthUser("u1", "m", "成员", "t1", List.of("MEMBER"), List.of());
        var effective = service.composeForProject(user, "p1");
        assertThat(effective.items()).extracting(GuidanceService.EffectiveItem::title).containsExactly("提交说明", "模块边界");
        assertThat(effective.items().get(0).body()).isEqualTo("公司要求带工单号");
        assertThat(effective.items()).noneMatch(i -> "架构笔记".equals(i.title()));
    }

    @Test
    void snapshotPersistsWithoutAgentSession() {
        when(guidanceItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                item("TENANT", "t1", null, "CONVENTION", "提交说明", "公司要求带工单号", true, true)));
        AuthUser user = new AuthUser("u1", "m", "成员", "t1", List.of("MEMBER"), List.of());
        var snap = service.snapshotForProject(user, "p1");
        assertThat(snap.id()).isNotBlank();
        assertThat(snap.compositionHash()).isNotBlank();
        assertThat(snap.items()).hasSize(1);
    }

    private static GuidanceItem item(
            String scope,
            String tenantId,
            String projectId,
            String kind,
            String title,
            String body,
            boolean enabled,
            boolean include) {
        GuidanceItem item = new GuidanceItem();
        item.setId(java.util.UUID.randomUUID().toString());
        item.setScope(scope);
        item.setTenantId(tenantId);
        item.setProjectId(projectId);
        item.setKind(kind);
        item.setTitle(title);
        item.setBody(body);
        item.setEnabled(enabled);
        item.setIncludeInAgent(include);
        return item;
    }
}
