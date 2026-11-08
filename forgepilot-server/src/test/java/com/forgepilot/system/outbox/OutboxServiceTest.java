package com.forgepilot.system.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.system.persistence.OutboxMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxServiceTest {

    private final OutboxMapper mapper = mock(OutboxMapper.class);
    private final InMemoryControlEventPublisher publisher = new InMemoryControlEventPublisher();
    private final List<OutboxEvent> store = new ArrayList<>();
    private OutboxService service;

    @BeforeEach
    void setUp() {
        service = new OutboxService(mapper, new ObjectMapper(), publisher);
        when(mapper.insert(any(OutboxEvent.class))).thenAnswer(inv -> {
            store.add(inv.getArgument(0));
            return 1;
        });
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> store.stream()
                .filter(e -> e.getPublishedAt() == null)
                .toList());
        when(mapper.updateById(any(OutboxEvent.class))).thenAnswer(inv -> {
            OutboxEvent e = inv.getArgument(0);
            store.removeIf(x -> x.getId().equals(e.getId()));
            store.add(e);
            return 1;
        });
    }

    @Test
    void enqueueRecordsEvenWithoutRabbitAndDrainPublishesToFake() {
        String id = service.enqueue(
                "NODE_ASSIGNED", "TASK", "task-1", null, Map.of("nodeKey", "n1", "token", "should-mask-if-sensitive"));
        assertThat(id).isNotBlank();
        assertThat(store).hasSize(1);
        assertThat(store.get(0).getPublishedAt()).isNull();
        assertThat(publisher.published()).isEmpty();

        int n = service.publishPending(10);
        assertThat(n).isEqualTo(1);
        assertThat(publisher.published()).hasSize(1);
        assertThat(publisher.published().get(0).eventType()).isEqualTo("NODE_ASSIGNED");
        assertThat(store.get(0).getPublishedAt()).isNotNull();
    }

    @Test
    void rejectsHighVolumeTokenStreamEvents() {
        assertThatThrownBy(() -> service.enqueue("TOKEN_STREAM_CHUNK", "SESSION", "s1", "s1", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("高频");
    }
}
