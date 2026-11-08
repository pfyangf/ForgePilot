package com.forgepilot.system.worker;

import com.forgepilot.execution.retention.WorkspaceRetentionService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "forgepilot.workers.workspace-retention.enabled", havingValue = "true", matchIfMissing = true)
public class WorkspaceRetentionWorker {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceRetentionWorker.class);
    private final WorkspaceRetentionService retentionService;

    public WorkspaceRetentionWorker(WorkspaceRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(fixedDelayString = "${forgepilot.workers.workspace-retention.delay-ms:60000}")
    public void cleanup() {
        int n = retentionService.cleanupEligible(Instant.now());
        if (n > 0) {
            log.info("workspace retention cleaned {}", n);
        }
    }
}
