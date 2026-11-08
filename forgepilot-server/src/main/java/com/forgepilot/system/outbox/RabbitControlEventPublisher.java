package com.forgepilot.system.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 可选 RabbitMQ 发布。缺少 RabbitTemplate 时不注册；Outbox 仍由内存 publisher 排空。
 */
@Component
@ConditionalOnProperty(name = "forgepilot.rabbit.enabled", havingValue = "true")
@ConditionalOnBean(RabbitTemplate.class)
public class RabbitControlEventPublisher implements ControlEventPublisher {

    public static final String EXCHANGE = "forgepilot.control";
    public static final String ROUTING_KEY = "control.event";

    private final ObjectProvider<RabbitTemplate> rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RabbitControlEventPublisher(
            ObjectProvider<RabbitTemplate> rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${forgepilot.rabbit.enabled:false}") boolean enabled) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    public void publish(PublishedEvent event) {
        if (!enabled) {
            throw new IllegalStateException("RabbitMQ publisher disabled");
        }
        RabbitTemplate template = rabbitTemplate.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("RabbitTemplate unavailable");
        }
        try {
            template.convertAndSend(EXCHANGE, ROUTING_KEY, objectMapper.writeValueAsString(Map.of(
                    "eventId", event.eventId(),
                    "eventType", event.eventType(),
                    "aggregateType", event.aggregateType(),
                    "aggregateId", event.aggregateId(),
                    "sessionId", event.sessionId() == null ? "" : event.sessionId(),
                    "payload", event.payload())));
        } catch (Exception ex) {
            throw new IllegalStateException("RabbitMQ publish failed: " + ex.getMessage(), ex);
        }
    }
}
