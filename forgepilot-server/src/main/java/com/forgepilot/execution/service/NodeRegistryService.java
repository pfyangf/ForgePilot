package com.forgepilot.execution.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.domain.ExecutionNode;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.execution.persistence.ExecutionNodeMapper;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.outbox.OutboxService;
import com.forgepilot.system.auth.AuthUser;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 执行节点注册、心跳与基于 PostgreSQL 的排队分配。
 * 并行任务硬隔离依赖各自工作区 / Session 凭证；仓库 bare mirror 只读共享，不对仓库加排他锁。
 */
@Service
public class NodeRegistryService {

    public static final String LOCAL_NODE_ID = "80000000-0000-4000-8000-000000000001";

    private final ExecutionNodeMapper executionNodeMapper;
    private final CodingTaskMapper codingTaskMapper;
    private final ActivityLogRecorder activityLogRecorder;
    private final OutboxService outboxService;
    private final Duration heartbeatTimeout;

    public NodeRegistryService(
            ExecutionNodeMapper executionNodeMapper,
            CodingTaskMapper codingTaskMapper,
            ActivityLogRecorder activityLogRecorder,
            OutboxService outboxService,
            @Value("${forgepilot.execution.heartbeat-timeout-seconds:45}") long heartbeatTimeoutSeconds) {
        this.executionNodeMapper = executionNodeMapper;
        this.codingTaskMapper = codingTaskMapper;
        this.activityLogRecorder = activityLogRecorder;
        this.outboxService = outboxService;
        this.heartbeatTimeout = Duration.ofSeconds(heartbeatTimeoutSeconds);
    }

    @Transactional
    public NodeView register(String nodeKey, int capacity, String labelsJson, String tenantIdForLog) {
        if (nodeKey == null || nodeKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 nodeKey");
        }
        if (capacity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "capacity 必须 >= 1");
        }
        ExecutionNode existing = executionNodeMapper.selectOne(new LambdaQueryWrapper<ExecutionNode>()
                .eq(ExecutionNode::getNodeKey, nodeKey));
        Instant now = Instant.now();
        if (existing == null) {
            ExecutionNode node = new ExecutionNode();
            node.setId(UUID.randomUUID().toString());
            node.setNodeKey(nodeKey);
            node.setCapacity(capacity);
            node.setLabels(labelsJson == null || labelsJson.isBlank() ? "{}" : labelsJson);
            node.setStatus("ONLINE");
            node.setLastHeartbeatAt(now);
            node.setActiveSessions(0);
            executionNodeMapper.insert(node);
            activityLogRecorder.record(
                    tenantIdForLog,
                    "SYSTEM",
                    null,
                    "NODE_REGISTER",
                    "EXECUTION_NODE",
                    node.getId(),
                    null,
                    null,
                    null,
                    null,
                    "节点注册：" + nodeKey,
                    Map.of("nodeKey", nodeKey, "capacity", capacity),
                    "SUCCESS");
            return toView(node);
        }
        existing.setCapacity(capacity);
        if (labelsJson != null && !labelsJson.isBlank()) {
            existing.setLabels(labelsJson);
        }
        existing.setStatus("ONLINE");
        existing.setLastHeartbeatAt(now);
        executionNodeMapper.updateById(existing);
        activityLogRecorder.record(
                tenantIdForLog,
                "SYSTEM",
                null,
                "NODE_REGISTER",
                "EXECUTION_NODE",
                existing.getId(),
                null,
                null,
                null,
                null,
                "节点重新注册：" + nodeKey,
                Map.of("nodeKey", nodeKey, "capacity", capacity),
                "SUCCESS");
        return toView(existing);
    }

    @Transactional
    public NodeView heartbeat(String nodeKey, int activeSessions) {
        ExecutionNode node = executionNodeMapper.selectOne(new LambdaQueryWrapper<ExecutionNode>()
                .eq(ExecutionNode::getNodeKey, nodeKey));
        if (node == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "节点未注册");
        }
        node.setActiveSessions(Math.max(0, activeSessions));
        node.setLastHeartbeatAt(Instant.now());
        node.setStatus("ONLINE");
        executionNodeMapper.updateById(node);
        return toView(node);
    }

    @Transactional
    public void markStaleNodesOffline() {
        Instant cutoff = Instant.now().minus(heartbeatTimeout);
        List<ExecutionNode> nodes = executionNodeMapper.selectList(new LambdaQueryWrapper<>());
        for (ExecutionNode node : nodes) {
            if ("ONLINE".equals(node.getStatus())
                    && (node.getLastHeartbeatAt() == null || node.getLastHeartbeatAt().isBefore(cutoff))) {
                node.setStatus("OFFLINE");
                executionNodeMapper.updateById(node);
            }
        }
    }

    public List<NodeView> listNodes() {
        markStaleNodesOffline();
        return executionNodeMapper.selectList(new LambdaQueryWrapper<ExecutionNode>().orderByAsc(ExecutionNode::getNodeKey))
                .stream()
                .map(this::toView)
                .toList();
    }

    /**
     * 为排队任务挑选一个在线且有余量的节点。不对仓库加排他锁。
     */
    @Transactional
    public Assignment assignNextPending(AuthUser actor) {
        markStaleNodesOffline();
        CodingTask task = codingTaskMapper.selectOne(new LambdaQueryWrapper<CodingTask>()
                .eq(CodingTask::getStatus, "PENDING")
                .orderByAsc(CodingTask::getId)
                .last("limit 1"));
        if (task == null) {
            return null;
        }
        ExecutionNode node = pickLiveNode();
        if (node == null) {
            return null;
        }
        task.setStatus("PREPARING");
        codingTaskMapper.updateById(task);
        node.setActiveSessions((node.getActiveSessions() == null ? 0 : node.getActiveSessions()) + 1);
        executionNodeMapper.updateById(node);
        activityLogRecorder.record(
                actor == null ? null : actor.tenantId(),
                "SYSTEM",
                actor == null ? "scheduler" : actor.userId(),
                "NODE_ASSIGN",
                "TASK",
                task.getId(),
                task.getProjectId(),
                task.getRequirementId(),
                task.getId(),
                null,
                "任务分配到节点 " + node.getNodeKey(),
                Map.of("nodeId", node.getId(), "nodeKey", node.getNodeKey(), "taskId", task.getId()),
                "SUCCESS");
        outboxService.enqueue(
                "NODE_ASSIGNED",
                "TASK",
                task.getId(),
                null,
                Map.of("nodeId", node.getId(), "nodeKey", node.getNodeKey(), "taskId", task.getId()));
        return new Assignment(task.getId(), node.getId(), node.getNodeKey());
    }

    public ExecutionNode pickLiveNode() {
        markStaleNodesOffline();
        return executionNodeMapper.selectList(new LambdaQueryWrapper<ExecutionNode>().eq(ExecutionNode::getStatus, "ONLINE"))
                .stream()
                .filter(n -> {
                    int active = n.getActiveSessions() == null ? 0 : n.getActiveSessions();
                    int cap = n.getCapacity() == null ? 0 : n.getCapacity();
                    return active < cap;
                })
                .min(Comparator.comparingInt(n -> n.getActiveSessions() == null ? 0 : n.getActiveSessions()))
                .orElse(null);
    }

    public ExecutionNode requireLiveNode(String nodeId) {
        ExecutionNode node = executionNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "节点不存在");
        }
        if (!"ONLINE".equals(node.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "节点不在线");
        }
        return node;
    }

    private NodeView toView(ExecutionNode node) {
        boolean stale = node.getLastHeartbeatAt() != null
                && node.getLastHeartbeatAt().isBefore(Instant.now().minus(heartbeatTimeout));
        return new NodeView(
                node.getId(),
                node.getNodeKey(),
                node.getCapacity(),
                node.getLabels(),
                stale ? "OFFLINE" : node.getStatus(),
                node.getLastHeartbeatAt(),
                node.getActiveSessions() == null ? 0 : node.getActiveSessions());
    }

    public record NodeView(
            String id,
            String nodeKey,
            Integer capacity,
            String labels,
            String status,
            Instant lastHeartbeatAt,
            int activeSessions) {
    }

    public record Assignment(String taskId, String nodeId, String nodeKey) {
    }
}
