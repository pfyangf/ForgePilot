package com.forgepilot.execution.retention;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.delivery.git.TaskWorkspaceLayout;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.domain.Workspace;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.execution.persistence.WorkspaceMapper;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.system.domain.RetentionPolicy;
import com.forgepilot.system.persistence.RetentionPolicyMapper;
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
 * 仅在任务进入 COMPLETED/FAILED/CANCELLED（以 task.terminal_at 为准）且超过宽限期后清理磁盘。
 * 宽限：租户 retention_policy 覆盖平台默认；再回退到 forgepilot.workspace.retention-grace-days。
 */
@Service
public class WorkspaceRetentionService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceRetentionService.class);
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED", "CANCELLED");

    private final CodingTaskMapper codingTaskMapper;
    private final WorkspaceMapper workspaceMapper;
    private final ProjectMapper projectMapper;
    private final RetentionPolicyMapper retentionPolicyMapper;
    private final Duration defaultGrace;
    private final String storageRoot;
    private final TaskWorkspaceLayout layout = new TaskWorkspaceLayout();

    public WorkspaceRetentionService(
            CodingTaskMapper codingTaskMapper,
            WorkspaceMapper workspaceMapper,
            ProjectMapper projectMapper,
            RetentionPolicyMapper retentionPolicyMapper,
            @Value("${forgepilot.workspace.retention-grace-days:7}") long graceDays,
            @Value("${forgepilot.workspace.storage-root:${java.io.tmpdir}/forgepilot-workspaces}") String storageRoot) {
        this.codingTaskMapper = codingTaskMapper;
        this.workspaceMapper = workspaceMapper;
        this.projectMapper = projectMapper;
        this.retentionPolicyMapper = retentionPolicyMapper;
        this.defaultGrace = Duration.ofDays(Math.max(0, graceDays));
        this.storageRoot = storageRoot;
    }

    /** 容器销毁钩子：只记录，不删工作区。 */
    public void onContainerStopped(String taskId, String containerRef) {
        log.info("container stopped task={} ref={} (workspace retained)", taskId, containerRef);
    }

    public boolean isPastGrace(Instant terminalAt, Instant now, Duration grace) {
        if (terminalAt == null) {
            return false;
        }
        return !now.isBefore(terminalAt.plus(grace));
    }

    public Duration resolveGrace(CodingTask task) {
        if (task == null || task.getProjectId() == null) {
            return defaultGrace;
        }
        Project project = projectMapper.selectById(task.getProjectId());
        if (project != null && project.getTenantId() != null) {
            RetentionPolicy tenant = retentionPolicyMapper.selectOne(new LambdaQueryWrapper<RetentionPolicy>()
                    .eq(RetentionPolicy::getScope, "TENANT")
                    .eq(RetentionPolicy::getTenantId, project.getTenantId()));
            if (tenant != null && tenant.getWorkspaceRetentionDays() != null) {
                return Duration.ofDays(Math.max(0, tenant.getWorkspaceRetentionDays()));
            }
        }
        RetentionPolicy platform = retentionPolicyMapper.selectOne(new LambdaQueryWrapper<RetentionPolicy>()
                .eq(RetentionPolicy::getScope, "PLATFORM")
                .isNull(RetentionPolicy::getTenantId));
        if (platform != null && platform.getWorkspaceRetentionDays() != null) {
            return Duration.ofDays(Math.max(0, platform.getWorkspaceRetentionDays()));
        }
        return defaultGrace;
    }

    public int cleanupEligible(Instant now) {
        int removed = 0;
        List<CodingTask> tasks = codingTaskMapper.selectList(new LambdaQueryWrapper<CodingTask>()
                .in(CodingTask::getStatus, TERMINAL));
        for (CodingTask task : tasks) {
            Instant terminalAt = task.getTerminalAt();
            if (terminalAt == null) {
                // 兼容旧数据：无 terminal_at 则跳过，避免误删
                continue;
            }
            Duration grace = resolveGrace(task);
            if (!isPastGrace(terminalAt, now, grace)) {
                continue;
            }
            Workspace ws = workspaceMapper.selectOne(
                    new LambdaQueryWrapper<Workspace>().eq(Workspace::getTaskId, task.getId()));
            if (ws == null || ws.getRootPath() == null || "DELETED".equals(ws.getStatus())) {
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
            } else {
                ws.setStatus("DELETED");
                workspaceMapper.updateById(ws);
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

    public Duration defaultGrace() {
        return defaultGrace;
    }
}
