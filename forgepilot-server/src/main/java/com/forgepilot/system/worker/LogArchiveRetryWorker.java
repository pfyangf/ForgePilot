package com.forgepilot.system.worker;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.execution.stream.SessionLogArchiveService;
import com.forgepilot.system.outbox.OutboxEvent;
import com.forgepilot.system.persistence.OutboxMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 排空 LOG_ARCHIVE_RETRY：再次尝试归档。成功则标记 outbox published；失败保留 unpublished。
 */
@Component
@ConditionalOnProperty(name = "forgepilot.workers.log-archive-retry.enabled", havingValue = "true", matchIfMissing = true)
public class LogArchiveRetryWorker {

    private static final Logger log = LoggerFactory.getLogger(LogArchiveRetryWorker.class);

    private final OutboxMapper outboxMapper;
    private final SessionLogArchiveService archiveService;
    private final ObjectMapper objectMapper;

    public LogArchiveRetryWorker(
            OutboxMapper outboxMapper, SessionLogArchiveService archiveService, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.archiveService = archiveService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${forgepilot.workers.log-archive-retry.delay-ms:15000}")
    public void retry() {
        List<OutboxEvent> pending = outboxMapper.selectList(new LambdaQueryWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventType, "LOG_ARCHIVE_RETRY")
                .isNull(OutboxEvent::getPublishedAt)
                .orderByAsc(OutboxEvent::getCreatedAt)
                .last("limit 20"));
        for (OutboxEvent row : pending) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(row.getPayload(), Map.class);
                String taskId = String.valueOf(payload.getOrDefault("taskId", ""));
                String sessionId = String.valueOf(payload.getOrDefault("sessionId", row.getSessionId()));
                String objectKey = payload.get("objectKey") == null ? null : String.valueOf(payload.get("objectKey"));
                var result = archiveService.archiveAndIndex(taskId, sessionId, objectKey);
                if (result.uploaded()) {
                    row.setPublishedAt(Instant.now());
                    outboxMapper.updateById(row);
                }
            } catch (Exception ex) {
                log.warn("log archive retry failed for {}: {}", row.getEventId(), ex.getMessage());
            }
        }
    }
}
