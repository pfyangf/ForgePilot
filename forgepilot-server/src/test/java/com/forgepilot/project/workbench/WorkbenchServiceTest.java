package com.forgepilot.project.workbench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.AgentSession;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.persistence.AgentSessionMapper;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.Requirement;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.ProjectMemberMapper;
import com.forgepilot.project.persistence.RequirementMapper;
import com.forgepilot.system.auth.AuthUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class WorkbenchServiceTest {

    private static final String TENANT_A = "10000000-0000-4000-8000-000000000001";
    private static final String TENANT_B = "10000000-0000-4000-8000-000000000002";
    private static final String PROJECT_A = "30000000-0000-4000-8000-000000000001";
    private static final String PROJECT_B = "30000000-0000-4000-8000-000000000002";
    private static final String USER_A = "20000000-0000-4000-8000-000000000003";

    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final ProjectMemberMapper projectMemberMapper = mock(ProjectMemberMapper.class);
    private final RequirementMapper requirementMapper = mock(RequirementMapper.class);
    private final CodingTaskMapper codingTaskMapper = mock(CodingTaskMapper.class);
    private final AgentSessionMapper agentSessionMapper = mock(AgentSessionMapper.class);
    private WorkbenchService service;

    @BeforeEach
    void setUp() {
        service = new WorkbenchService(
                projectMapper, projectMemberMapper, requirementMapper, codingTaskMapper, agentSessionMapper);
        when(projectMemberMapper.findProjectIdsByUserId(USER_A)).thenReturn(List.of(PROJECT_A));
        when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(project(PROJECT_A, TENANT_A, "支付")));
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                requirement("r-mine", PROJECT_A, USER_A, USER_A, "待分析"),
                requirement("r-other", PROJECT_A, "boss", "boss", "待开发")));
        when(codingTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                task("t-wait", "r-mine", PROJECT_A, USER_A, USER_A, "WAITING_APPROVAL"),
                task("t-peer", "r-other", PROJECT_A, "boss", "boss", "PENDING"),
                task("t-foreign", "r-foreign", PROJECT_B, "x", "x", "PENDING")));
        AgentSession session = new AgentSession();
        session.setTaskId("t-wait");
        session.setStatus("SUCCEEDED");
        when(agentSessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(session));
    }

    @Test
    void mineScopeHidesPeerItemsButKeepsOwnWaiting() {
        AuthUser user = new AuthUser(USER_A, "demo_member", "演示成员", TENANT_A, List.of("MEMBER"), List.of("dashboard"));

        WorkbenchSnapshot mine = service.load(user, WorkbenchScope.MINE);
        assertThat(mine.requirements()).extracting(WorkbenchRequirementItem::id).containsExactly("r-mine");
        assertThat(mine.tasks()).extracting(WorkbenchTaskItem::id).containsExactly("t-wait");

        WorkbenchSnapshot waiting = service.load(user, WorkbenchScope.WAITING);
        assertThat(waiting.tasks()).extracting(WorkbenchTaskItem::id).containsExactly("t-wait");
        assertThat(waiting.counts().approval()).isEqualTo(1);

        WorkbenchSnapshot member = service.load(user, WorkbenchScope.MEMBER);
        assertThat(member.requirements()).extracting(WorkbenchRequirementItem::id).containsExactlyInAnyOrder("r-mine", "r-other");
        assertThat(member.tasks()).extracting(WorkbenchTaskItem::id).containsExactlyInAnyOrder("t-wait", "t-peer");
        assertThat(member.tasks()).extracting(WorkbenchTaskItem::id).doesNotContain("t-foreign");
    }

    @Test
    void otherTenantUserCannotSeeDemoProjectEvenIfIdsLeak() {
        when(projectMemberMapper.findProjectIdsByUserId("other")).thenReturn(List.of(PROJECT_B));
        when(projectMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(project(PROJECT_B, TENANT_B, "对账")));
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                requirement("r-foreign", PROJECT_B, "other", "other", "待开发")));
        when(codingTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                task("t-foreign", "r-foreign", PROJECT_B, "other", "other", "PENDING")));
        when(agentSessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        AuthUser other = new AuthUser("other", "other_member", "另一家", TENANT_B, List.of("MEMBER"), List.of("dashboard"));
        WorkbenchSnapshot snapshot = service.load(other, WorkbenchScope.MEMBER);
        assertThat(snapshot.requirements()).extracting(WorkbenchRequirementItem::id).containsExactly("r-foreign");
        assertThatThrownBy(() -> service.requirement(other, "r-mine"))
                .isInstanceOf(ResponseStatusException.class);
    }

    private static Project project(String id, String tenantId, String name) {
        Project project = new Project();
        project.setId(id);
        project.setTenantId(tenantId);
        project.setName(name);
        return project;
    }

    private static Requirement requirement(String id, String projectId, String createdBy, String ownerId, String status) {
        Requirement requirement = new Requirement();
        requirement.setId(id);
        requirement.setProjectId(projectId);
        requirement.setTitle(id);
        requirement.setCreatedBy(createdBy);
        requirement.setOwnerId(ownerId);
        requirement.setStatus(status);
        requirement.setDescription("draft");
        return requirement;
    }

    private static CodingTask task(
            String id, String requirementId, String projectId, String createdBy, String ownerId, String status) {
        CodingTask task = new CodingTask();
        task.setId(id);
        task.setRequirementId(requirementId);
        task.setProjectId(projectId);
        task.setCreatedBy(createdBy);
        task.setOwnerId(ownerId);
        task.setStatus(status);
        task.setTitle(id);
        task.setBranchName("fp/" + id);
        return task;
    }
}
