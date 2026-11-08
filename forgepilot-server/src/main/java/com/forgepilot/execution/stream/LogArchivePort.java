package com.forgepilot.execution.stream;

/** 完整日志归档到 MinIO 的端口。MVP 可用测试假实现。 */
public interface LogArchivePort {

    void archive(String taskId, String sessionId, String objectKeyHint);

    record ArchiveResult(String objectKey, boolean uploaded) {
    }
}
