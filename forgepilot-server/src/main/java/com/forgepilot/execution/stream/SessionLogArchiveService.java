package com.forgepilot.execution.stream;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forgepilot.execution.domain.Artifact;
import com.forgepilot.execution.persistence.ArtifactMapper;
import com.forgepilot.system.outbox.OutboxService;
import com.forgepilot.system.storage.ObjectStoragePort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 将会话日志块归档到对象存储。失败不删除 Redis/内存块，并通过 Outbox 记录重试意图。
 */
@Service
public class SessionLogArchiveService implements LogArchivePort {

    private final SessionLogStream sessionLogStream;
    private final ObjectStoragePort objectStorage;
    private final ArtifactMapper artifactMapper;
    private final OutboxService outboxService;
    private final String bucket;

    public SessionLogArchiveService(
            SessionLogStream sessionLogStream,
            ObjectStoragePort objectStorage,
            ArtifactMapper artifactMapper,
            OutboxService outboxService,
            @Value("${forgepilot.minio.bucket:forgepilot}") String bucket) {
        this.sessionLogStream = sessionLogStream;
        this.objectStorage = objectStorage;
        this.artifactMapper = artifactMapper;
        this.outboxService = outboxService;
        this.bucket = bucket;
    }

    @Override
    @Transactional
    public void archive(String taskId, String sessionId, String objectKeyHint) {
        archiveAndIndex(taskId, sessionId, objectKeyHint);
    }

    @Transactional
    public ArchiveResult archiveAndIndex(String taskId, String sessionId, String objectKeyHint) {
        String objectKey = objectKeyHint == null || objectKeyHint.isBlank()
                ? "logs/" + taskId + "/" + sessionId + ".log"
                : objectKeyHint;
        List<SessionLogStream.LogChunk> chunks = sessionLogStream.readAfter(sessionId, 0, 10_000);
        StringBuilder body = new StringBuilder();
        for (SessionLogStream.LogChunk chunk : chunks) {
            body.append(chunk.body()).append('\n');
        }
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try {
            ObjectStoragePort.PutResult put =
                    objectStorage.put(bucket, objectKey, bytes, "text/plain; charset=utf-8");
            Artifact artifact = new Artifact();
            artifact.setId(UUID.randomUUID().toString());
            artifact.setTaskId(taskId);
            artifact.setSessionId(sessionId);
            artifact.setKind("SESSION_LOG");
            artifact.setObjectKey(bucket + "/" + objectKey);
            artifact.setChecksum(sha256(bytes));
            artifact.setByteSize(put.byteSize());
            artifact.setCreatedAt(Instant.now());
            artifactMapper.insert(artifact);
            outboxService.enqueue(
                    "LOG_ARCHIVED",
                    "ARTIFACT",
                    artifact.getId(),
                    sessionId,
                    Map.of(
                            "taskId", taskId,
                            "sessionId", sessionId,
                            "objectKey", artifact.getObjectKey(),
                            "byteSize", put.byteSize()));
            return new ArchiveResult(artifact.getObjectKey(), true);
        } catch (RuntimeException ex) {
            outboxService.enqueue(
                    "LOG_ARCHIVE_RETRY",
                    "SESSION",
                    sessionId,
                    sessionId,
                    Map.of(
                            "taskId", taskId,
                            "sessionId", sessionId,
                            "objectKey", objectKey,
                            "error", ex.getMessage() == null ? "" : ex.getMessage()));
            // 不删除 stream 中的块
            return new ArchiveResult(objectKey, false);
        }
    }

    public Optional<Artifact> findLogArtifact(String sessionId) {
        return Optional.ofNullable(artifactMapper.selectOne(new LambdaQueryWrapper<Artifact>()
                .eq(Artifact::getSessionId, sessionId)
                .eq(Artifact::getKind, "SESSION_LOG")
                .orderByDesc(Artifact::getCreatedAt)
                .last("limit 1")));
    }

    public Optional<byte[]> fetchArchived(String sessionId) {
        return findLogArtifact(sessionId).flatMap(a -> {
            String key = a.getObjectKey();
            String b = bucket;
            String object = key;
            if (key.contains("/")) {
                int slash = key.indexOf('/');
                // stored as bucket/object or just object
                if (key.startsWith(bucket + "/")) {
                    object = key.substring(bucket.length() + 1);
                } else {
                    object = key.substring(slash + 1);
                    b = key.substring(0, slash);
                }
            }
            return objectStorage.get(b, object);
        });
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            return "";
        }
    }
}
