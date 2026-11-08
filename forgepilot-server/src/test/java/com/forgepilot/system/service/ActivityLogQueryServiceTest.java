package com.forgepilot.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.domain.ActivityLog;
import com.forgepilot.system.persistence.ActivityLogMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import com.forgepilot.system.web.ActivityLogController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityLogQueryServiceTest {

    private final ActivityLogMapper activityLogMapper = mock(ActivityLogMapper.class);
    private final CodingTaskMapper codingTaskMapper = mock(CodingTaskMapper.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final ProjectMemberMapper projectMemberMapper = mock(ProjectMemberMapper.class);
    private ActivityLogQueryService service;

    @BeforeEach
    void setUp() {
        ProjectAccess access = new ProjectAccess(projectMapper, projectMemberMapper);
        service = new ActivityLogQueryService(activityLogMapper, codingTaskMapper, access, new RoleAccess());
        when(projectMemberMapper.findProjectIdsByUserId("u1")).thenReturn(List.of("p1"));
        when(codingTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
    }

    @Test
    void tenantIsolationBlocksOtherCompanyFilter() {
        AuthUser user = new AuthUser("u1", "m", "成员", "t1", List.of("MEMBER"), List.of());
        assertThatThrownBy(() -> service.query(user, "t2", null, null, null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void payloadsAreMaskedInViews() {
        ActivityLog log = new ActivityLog();
        log.setId("l1");
        log.setTenantId("t1");
        log.setActorKind("USER");
        log.setActorUserId("u1");
        log.setOccurredAt(Instant.parse("2026-11-08T12:00:00Z"));
        log.setAction("MODEL_PROVIDER_CREATE");
        log.setTargetType("MODEL_PROVIDER");
        log.setSummary("创建");
        log.setResult("SUCCESS");
        log.setPayload(Map.of("apiKey", "sk-live", "name", "local"));
        log.setProjectId("p1");
        when(activityLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(log));

        AuthUser user = new AuthUser("u1", "m", "成员", "t1", List.of("MEMBER"), List.of());
        var rows = service.query(user, null, null, null, null, null, null);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).payload().get("apiKey")).isEqualTo("***");
        assertThat(rows.get(0).payload().get("name")).isEqualTo("local");
    }

    @Test
    void activityLogHttpMutationsAreRejected() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ActivityLogController(service)).build();
        mvc.perform(delete("/api/v1/activity-logs/l1")).andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/v1/activity-logs/l1").content("{}")).andExpect(status().isMethodNotAllowed());
    }
}
