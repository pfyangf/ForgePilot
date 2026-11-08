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
    private final List<Workspace> workspaces = new ArrayList<>();
    private WorkspaceRetentionService retention;

    @BeforeEach
    void setUp() throws Exception {
        retention = new WorkspaceRetentionService(taskMapper, workspaceMapper, 7, temp.toString());
        when(workspaceMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(inv ->
                workspaces.isEmpty() ? null : workspaces.get(0));
        when(workspaceMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> List.copyOf(workspaces));
        when(workspaceMapper.updateById(any(Workspace.class))).thenReturn(1);
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            CodingTask t = new CodingTask();
            t.setId("task-1");
            t.setStatus("COMPLETED");
            return List.of(t);
        });
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

        RetainingContainerStop stop = new RetainingContainerStop(retention);
        stop.stop("task-1", "agent-container-1");

        assertThat(Files.exists(root.resolve("keep.txt"))).isTrue();
        assertThat(retention.cleanupEligible(Instant.now())).isZero(); // still within grace
        assertThat(Files.exists(root.resolve("keep.txt"))).isTrue();
    }

    @Test
    void cleanupAfterGraceDeletesWorkspace() throws Exception {
        Path root = temp.resolve("task-old");
        Files.createDirectories(root);
        Files.writeString(root.resolve("gone.txt"), "x");
        Workspace ws = new Workspace();
        ws.setId("ws-2");
        ws.setTaskId("task-1");
        ws.setRootPath(root.toString());
        ws.setStatus("READY");
        ws.setCreatedAt(Instant.now().minusSeconds(8L * 24 * 3600));
        workspaces.add(ws);

        int removed = retention.cleanupEligible(Instant.now());
        assertThat(removed).isEqualTo(1);
        assertThat(Files.exists(root)).isFalse();
    }
}
