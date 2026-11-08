package com.forgepilot.system.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.system.persistence.OutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.system.activity.ActivityPayloadMasker;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事务性 Outbox：状态变更与待投递控制事件同事务写入 PostgreSQL。
 * 高频日志禁止 enqueue。
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final ControlEventPublisher publisher;

    public OutboxService(OutboxMapper outboxMapper, ObjectMapper objectMapper, ControlEventPublisher publisher) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
        this.publisher = publisher;
    }

    @Transactional
    public String enqueue(
            String eventType, String aggregateType, String aggregateId, String sessionId, Map<String, Object> payload) {
        if (eventType != null && eventType.toUpperCase().contains("TOKEN_STREAM")) {
            throw new IllegalArgumentException("高频 token/日志不得写入 Outbox/RabbitMQ");
        }
        Map<String, Object> safe = ActivityPayloadMasker.mask(payload == null ? Map.of() : payload);
        OutboxEvent row = new OutboxEvent();
        row.setId(UUID.randomUUID().toString());
        row.setEventId(UUID.randomUUID().toString());
        row.setEventType(eventType);
        row.setAggregateType(aggregateType);
        row.setAggregateId(aggregateId);
        row.setSessionId(sessionId);
        try {
            row.setPayload(objectMapper.writeValueAsString(safe));
        } catch (Exception ex) {
            row.setPayload("{}");
        }
        row.setCreatedAt(Instant.now());
        outboxMapper.insert(row);
        return row.getEventId();
    }

    /** 排空未发布事件；测试可对假 broker 调用。失败保留 unpublished。 */
    @Transactional
    public int publishPending(int limit) {
        List<OutboxEvent> pending = outboxMapper.selectList(new LambdaQueryWrapper<OutboxEvent>()
                .isNull(OutboxEvent::getPublishedAt)
                .orderByAsc(OutboxEvent::getCreatedAt)
                .last("limit " + Math.max(1, limit)));
        int published = 0;
        for (OutboxEvent row : pending) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(row.getPayload(), Map.class);
                publisher.publish(new ControlEventPublisher.PublishedEvent(
                        row.getEventId(),
                        row.getEventType(),
                        row.getAggregateType(),
                        row.getAggregateId(),
                        row.getSessionId(),
                        payload == null ? Map.of() : payload));
                row.setPublishedAt(Instant.now());
                outboxMapper.updateById(row);
                published++;
            } catch (Exception ex) {
                log.warn("Outbox publish failed for {}: {}", row.getEventId(), ex.getMessage());
            }
        }
        return published;
    }

    public List<OutboxEvent> unpublished() {
        return outboxMapper.selectList(new LambdaQueryWrapper<OutboxEvent>()
                .isNull(OutboxEvent::getPublishedAt)
                .orderByAsc(OutboxEvent::getCreatedAt));
    }
}
