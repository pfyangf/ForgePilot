package com.forgepilot.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.CodingTask;
import com.forgepilot.execution.domain.ExecutionNode;
import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.execution.persistence.ExecutionNodeMapper;
import com.forgepilot.system.activity.ActivityLogRecorder;
import com.forgepilot.system.outbox.InMemoryControlEventPublisher;
import com.forgepilot.system.outbox.OutboxService;
import com.forgepilot.system.persistence.OutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodeRegistryServiceTest {

    private final ExecutionNodeMapper nodeMapper = mock(ExecutionNodeMapper.class);
    private final CodingTaskMapper taskMapper = mock(CodingTaskMapper.class);
    private final ActivityLogRecorder recorder = mock(ActivityLogRecorder.class);
    private final List<ExecutionNode> store = new ArrayList<>();
    private NodeRegistryService service;

    @BeforeEach
    void setUp() {
        OutboxService outbox = new OutboxService(mock(OutboxMapper.class), new ObjectMapper(), new InMemoryControlEventPublisher());
        service = new NodeRegistryService(nodeMapper, taskMapper, recorder, outbox, 45);
        when(nodeMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(inv -> store.stream().findFirst().orElse(null));
        when(nodeMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> List.copyOf(store));
        when(nodeMapper.selectById(any())).thenAnswer(inv -> store.stream()
                .filter(n -> n.getId().equals(inv.getArgument(0)))
                .findFirst()
                .orElse(null));
        when(nodeMapper.insert(any(ExecutionNode.class))).thenAnswer(inv -> {
            store.add(inv.getArgument(0));
            return 1;
        });
        when(nodeMapper.updateById(any(ExecutionNode.class))).thenAnswer(inv -> {
            ExecutionNode n = inv.getArgument(0);
            store.removeIf(x -> x.getId().equals(n.getId()));
            store.add(n);
            return 1;
        });
    }

    @Test
    void registerHeartbeatAndAssignPendingTask() {
        var view = service.register("fake-node", 2, "{\"kind\":\"fake\"}", "t1");
        assertThat(view.status()).isEqualTo("ONLINE");
        assertThat(service.heartbeat("fake-node", 0).activeSessions()).isZero();

        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setProjectId("p1");
        task.setRequirementId("r1");
        task.setStatus("PENDING");
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);
        when(taskMapper.updateById(any(CodingTask.class))).thenReturn(1);

        var assignment = service.assignNextPending(null);
        assertThat(assignment).isNotNull();
        assertThat(assignment.nodeKey()).isEqualTo("fake-node");
        assertThat(assignment.taskId()).isEqualTo("task-1");
        assertThat(store.get(0).getActiveSessions()).isEqualTo(1);
    }

    @Test
    void pickLiveNodeRespectsCapacity() {
        ExecutionNode full = new ExecutionNode();
        full.setId("n1");
        full.setNodeKey("full");
        full.setCapacity(1);
        full.setStatus("ONLINE");
        full.setActiveSessions(1);
        full.setLastHeartbeatAt(Instant.now());
        store.add(full);
        assertThat(service.pickLiveNode()).isNull();

        full.setActiveSessions(0);
        assertThat(service.pickLiveNode().getNodeKey()).isEqualTo("full");
    }
}
