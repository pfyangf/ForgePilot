package com.forgepilot.execution.retention;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.delivery.git.TaskWorkspaceLayout;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.domain.Workspace;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.execution.persistence.WorkspaceMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 工作区保留策略：停止 Agent/验证容器不得删除工作区。
 * 仅在任务进入 COMPLETED/FAILED/CANCELLED 且超过宽限期后清理磁盘。
 */
@Service
public class WorkspaceRetentionService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceRetentionService.class);
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED", "CANCELLED");

    private final CodingTaskMapper codingTaskMapper;
    private final WorkspaceMapper workspaceMapper;
    private final Duration grace;
    private final String storageRoot;
    private final TaskWorkspaceLayout layout = new TaskWorkspaceLayout();

    public WorkspaceRetentionService(
            CodingTaskMapper codingTaskMapper,
            WorkspaceMapper workspaceMapper,
            @Value("${forgepilot.workspace.retention-grace-days:7}") long graceDays,
            @Value("${forgepilot.workspace.storage-root:${java.io.tmpdir}/forgepilot-workspaces}") String storageRoot) {
        this.codingTaskMapper = codingTaskMapper;
        this.workspaceMapper = workspaceMapper;
        this.grace = Duration.ofDays(Math.max(0, graceDays));
        this.storageRoot = storageRoot;
    }

    /** 容器销毁钩子：只记录，不删工作区。 */
    public void onContainerStopped(String taskId, String containerRef) {
        log.info("container stopped task={} ref={} (workspace retained)", taskId, containerRef);
    }

    public boolean shouldDeleteWorkspace(CodingTask task, Instant now) {
        if (task == null || !TERMINAL.contains(task.getStatus())) {
            return false;
        }
        Instant ended = task.getQueuedAt() != null ? task.getQueuedAt() : Instant.EPOCH;
        // prefer updated semantics: use a synthetic end = now for tests via explicit endedAt param
        return true; // terminal check only; grace applied in cleanupEligible with endedAt
    }

    public boolean isPastGrace(Instant terminalAt, Instant now) {
        if (terminalAt == null) {
            return false;
        }
        return !now.isBefore(terminalAt.plus(grace));
    }

    public int cleanupEligible(Instant now) {
        int removed = 0;
        List<CodingTask> tasks = codingTaskMapper.selectList(new LambdaQueryWrapper<CodingTask>()
                .in(CodingTask::getStatus, TERMINAL));
        for (CodingTask task : tasks) {
            Workspace ws = workspaceMapper.selectOne(new LambdaQueryWrapper<Workspace>().eq(Workspace::getTaskId, task.getId()));
            if (ws == null || ws.getRootPath() == null) {
                continue;
            }
            Instant terminalAt = ws.getCreatedAt() != null ? ws.getCreatedAt() : Instant.EPOCH;
            // MVP：用 workspace.createdAt 近似；生产应记录 terminalAt
            if (!isPastGrace(terminalAt, now)) {
                continue;
            }
            Path root = Path.of(ws.getRootPath());
            if (Files.exists(root)) {
                try {
                    deleteRecursive(root);
                    removed++;
                    ws.setStatus("DELETED");
                    workspaceMapper.updateById(ws);
                } catch (Exception ex) {
                    log.warn("workspace cleanup failed for {}: {}", task.getId(), ex.getMessage());
                }
            }
        }
        return removed;
    }

    public Path workspaceRoot(String taskId) {
        return Path.of(layout.taskRoot(storageRoot, taskId));
    }

    private static void deleteRecursive(Path path) throws Exception {
        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }

    public Duration grace() {
        return grace;
    }
}
