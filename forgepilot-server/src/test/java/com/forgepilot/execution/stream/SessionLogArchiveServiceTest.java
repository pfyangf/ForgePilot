package com.forgepilot.execution.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.execution.domain.Artifact;
import com.forgepilot.execution.persistence.ArtifactMapper;
import com.forgepilot.system.outbox.InMemoryControlEventPublisher;
import com.forgepilot.system.outbox.OutboxService;
import com.forgepilot.system.persistence.OutboxMapper;
import com.forgepilot.system.storage.InMemoryObjectStorage;
import com.forgepilot.system.storage.ObjectStoragePort;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionLogArchiveServiceTest {

    private final InMemorySessionLogStream stream = new InMemorySessionLogStream();
    private final InMemoryObjectStorage storage = new InMemoryObjectStorage();
    private final ArtifactMapper artifactMapper = mock(ArtifactMapper.class);
    private final List<Artifact> artifacts = new ArrayList<>();
    private final List<com.forgepilot.system.outbox.OutboxEvent> outbox = new ArrayList<>();
    private SessionLogArchiveService service;

    @BeforeEach
    void setUp() {
        OutboxMapper outboxMapper = mock(OutboxMapper.class);
        when(outboxMapper.insert(any(com.forgepilot.system.outbox.OutboxEvent.class))).thenAnswer(inv -> {
            outbox.add(inv.getArgument(0));
            return 1;
        });
        when(artifactMapper.insert(any(Artifact.class))).thenAnswer(inv -> {
            artifacts.add(inv.getArgument(0));
            return 1;
        });
        when(artifactMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(inv ->
                artifacts.isEmpty() ? null : artifacts.get(artifacts.size() - 1));
        service = new SessionLogArchiveService(
                stream,
                storage,
                artifactMapper,
                new OutboxService(outboxMapper, new ObjectMapper(), new InMemoryControlEventPublisher()),
                "forgepilot");
        stream.append("sess-1", "line-one secret=REDACT_ME");
        stream.append("sess-1", "line-two");
    }

    @Test
    void archivesToFakeStorageAndKeepsStreamUntilSuccess() {
        var result = service.archiveAndIndex("task-1", "sess-1", null);
        assertThat(result.uploaded()).isTrue();
        assertThat(storage.exists("forgepilot", "logs/task-1/sess-1.log")).isTrue();
        assertThat(artifacts).hasSize(1);
        assertThat(stream.readAfter("sess-1", 0, 100)).hasSize(2);
        assertThat(service.fetchArchived("sess-1")).isPresent();
        assertThat(new String(service.fetchArchived("sess-1").orElseThrow())).contains("line-one");
    }

    @Test
    void failureEnqueuesRetryAndDoesNotDropChunks() {
        ObjectStoragePort failing = new ObjectStoragePort() {
            @Override
            public PutResult put(String bucket, String objectKey, byte[] content, String contentType) {
                throw new IllegalStateException("minio down");
            }

            @Override
            public java.util.Optional<byte[]> get(String bucket, String objectKey) {
                return java.util.Optional.empty();
            }

            @Override
            public boolean exists(String bucket, String objectKey) {
                return false;
            }

            @Override
            public java.util.List<String> listKeys(String bucket, String prefix) {
                return List.of();
            }
        };
        OutboxMapper outboxMapper = mock(OutboxMapper.class);
        AtomicBoolean sawRetry = new AtomicBoolean();
        when(outboxMapper.insert(any(com.forgepilot.system.outbox.OutboxEvent.class))).thenAnswer(inv -> {
            com.forgepilot.system.outbox.OutboxEvent e = inv.getArgument(0);
            if ("LOG_ARCHIVE_RETRY".equals(e.getEventType())) {
                sawRetry.set(true);
            }
            return 1;
        });
        SessionLogArchiveService failingService = new SessionLogArchiveService(
                stream,
                failing,
                artifactMapper,
                new OutboxService(outboxMapper, new ObjectMapper(), new InMemoryControlEventPublisher()),
                "forgepilot");
        var result = failingService.archiveAndIndex("task-1", "sess-1", null);
        assertThat(result.uploaded()).isFalse();
        assertThat(sawRetry.get()).isTrue();
        assertThat(stream.readAfter("sess-1", 0, 100)).hasSize(2);
    }
}
