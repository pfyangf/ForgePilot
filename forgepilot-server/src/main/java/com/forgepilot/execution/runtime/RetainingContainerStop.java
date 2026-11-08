package com.forgepilot.execution.runtime;

import com.forgepilot.execution.retention.WorkspaceRetentionService;
import org.springframework.stereotype.Component;

@Component
public class RetainingContainerStop implements ContainerStopPort {

    private final WorkspaceRetentionService retentionService;

    public RetainingContainerStop(WorkspaceRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Override
    public void stop(String taskId, String containerRef) {
        retentionService.onContainerStopped(taskId, containerRef);
    }
}
