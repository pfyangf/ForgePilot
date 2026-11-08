package com.forgepilot.system.outbox;

import java.util.Map;

/**
 * 控制/状态事件发布端口。高频 Agent 日志不得走此通道。
 */
public interface ControlEventPublisher {

    void publish(PublishedEvent event);

    record PublishedEvent(
            String eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            String sessionId,
            Map<String, Object> payload) {
    }
}
