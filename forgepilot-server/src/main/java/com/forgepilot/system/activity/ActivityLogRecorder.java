package com.forgepilot.system.activity;

import com.forgepilot.system.domain.ActivityLog;
import com.forgepilot.system.persistence.ActivityLogMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 只追加操作记录。载荷在落库前打码。 */
@Service
public class ActivityLogRecorder {

    private final ActivityLogMapper activityLogMapper;

    public ActivityLogRecorder(ActivityLogMapper activityLogMapper) {
        this.activityLogMapper = activityLogMapper;
    }

    public void record(
            String tenantId,
            String actorKind,
            String actorUserId,
            String action,
            String targetType,
            String targetId,
            String projectId,
            String requirementId,
            String taskId,
            String requestId,
            String summary,
            Map<String, Object> payload,
            String result) {
        ActivityLog log = new ActivityLog();
        log.setId(UUID.randomUUID().toString());
        log.setTenantId(tenantId);
        log.setActorKind(actorKind);
        log.setActorUserId(actorUserId);
        log.setOccurredAt(Instant.now());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setProjectId(projectId);
        log.setRequirementId(requirementId);
        log.setTaskId(taskId);
        log.setRequestId(requestId);
        log.setSummary(summary);
        log.setPayload(ActivityPayloadMasker.mask(payload));
        log.setResult(result);
        activityLogMapper.insert(log);
    }
}
