package com.forgepilot.system.worker;

import com.forgepilot.system.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "forgepilot.workers.outbox-drain.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDrainWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxDrainWorker.class);
    private final OutboxService outboxService;

    public OutboxDrainWorker(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Scheduled(fixedDelayString = "${forgepilot.workers.outbox-drain.delay-ms:5000}")
    public void drain() {
        int n = outboxService.publishPending(100);
        if (n > 0) {
            log.debug("outbox drained {} events", n);
        }
    }
}
