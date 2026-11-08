package com.forgepilot.system.outbox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** 单元测试与无 RabbitMQ 时的假 broker。 */
@Primary
@Component
@ConditionalOnProperty(name = "forgepilot.rabbit.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryControlEventPublisher implements ControlEventPublisher {

    private final CopyOnWriteArrayList<PublishedEvent> published = new CopyOnWriteArrayList<>();

    @Override
    public void publish(PublishedEvent event) {
        published.add(event);
    }

    public List<PublishedEvent> published() {
        return List.copyOf(published);
    }

    public void clear() {
        published.clear();
    }
}
