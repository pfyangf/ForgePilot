package com.forgepilot.execution.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.domain.Workspace;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.execution.persistence.WorkspaceMapper;
import com.forgepilot.execution.runtime.RetainingContainerStop;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.system.domain.RetentionPolicy;
import com.forgepilot.system.persistence.RetentionPolicyMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceRetentionServiceTest {

    @TempDir
    Path temp;

    private final CodingTaskMapper taskMapper = mock(CodingTaskMapper.class);
    private final WorkspaceMapper workspaceMapper = mock(WorkspaceMapper.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final RetentionPolicyMapper retentionPolicyMapper = mock(RetentionPolicyMapper.class);
    private final List<Workspace> workspaces = new ArrayList<>();
    private final List<CodingTask> tasks = new ArrayList<>();
    private WorkspaceRetentionService retention;

    @BeforeEach
    void setUp() {
        retention = new WorkspaceRetentionService(
                taskMapper, workspaceMapper, projectMapper, retentionPolicyMapper, 7, temp.toString());
        when(workspaceMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(inv ->
                workspaces.isEmpty() ? null : workspaces.get(0));
        when(workspaceMapper.updateById(any(Workspace.class))).thenReturn(1);
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> List.copyOf(tasks));
        when(projectMapper.selectById(any())).thenAnswer(inv -> {
            Project p = new Project();
            p.setId("p1");
            p.setTenantId("t1");
            return p;
        });
        when(retentionPolicyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
    }

    @Test
    void containerStopDoesNotWipeWorkspace() throws Exception {
        Path root = temp.resolve("task-1");
        Files.createDirectories(root);
        Files.writeString(root.resolve("keep.txt"), "alive");
        Workspace ws = new Workspace();
        ws.setId("ws-1");
        ws.setTaskId("task-1");
        ws.setRootPath(root.toString());
        ws.setStatus("READY");
        ws.setCreatedAt(Instant.now());
        workspaces.add(ws);

        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setProjectId("p1");
        task.setStatus("COMPLETED");
        task.setTerminalAt(Instant.now());
        tasks.add(task);

        RetainingContainerStop stop = new RetainingContainerStop(retention);
        stop.stop("task-1", "agent-container-1");

        assertThat(Files.exists(root.resolve("keep.txt"))).isTrue();
        assertThat(retention.cleanupEligible(Instant.now())).isZero();
        assertThat(Files.exists(root.resolve("keep.txt"))).isTrue();
    }

    @Test
    void cleanupUsesTerminalAtNotCreatedAt() throws Exception {
        Path root = temp.resolve("task-old");
        Files.createDirectories(root);
        Files.writeString(root.resolve("gone.txt"), "x");
        Workspace ws = new Workspace();
        ws.setId("ws-2");
        ws.setTaskId("task-1");
        ws.setRootPath(root.toString());
        ws.setStatus("READY");
        ws.setCreatedAt(Instant.now()); // 新建工作区也不应误删
        workspaces.add(ws);

        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setProjectId("p1");
        task.setStatus("COMPLETED");
        task.setTerminalAt(Instant.now().minusSeconds(8L * 24 * 3600));
        tasks.add(task);

        int removed = retention.cleanupEligible(Instant.now());
        assertThat(removed).isEqualTo(1);
        assertThat(Files.exists(root)).isFalse();
    }

    @Test
    void skipsWhenTerminalAtMissing() throws Exception {
        Path root = temp.resolve("task-legacy");
        Files.createDirectories(root);
        Workspace ws = new Workspace();
        ws.setId("ws-3");
        ws.setTaskId("task-1");
        ws.setRootPath(root.toString());
        ws.setStatus("READY");
        ws.setCreatedAt(Instant.EPOCH);
        workspaces.add(ws);
        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setProjectId("p1");
        task.setStatus("COMPLETED");
        task.setTerminalAt(null);
        tasks.add(task);
        assertThat(retention.cleanupEligible(Instant.now())).isZero();
        assertThat(Files.exists(root)).isTrue();
    }

    @Test
    void tenantPolicyOverridesDefaultGrace() {
        RetentionPolicy tenant = new RetentionPolicy();
        tenant.setScope("TENANT");
        tenant.setTenantId("t1");
        tenant.setWorkspaceRetentionDays(1);
        when(retentionPolicyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(tenant);
        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setProjectId("p1");
        assertThat(retention.resolveGrace(task).toDays()).isEqualTo(1);
    }
}
