package com.forgepilot.execution.stream;

/** 完整日志归档到对象存储的端口。 */
public interface LogArchivePort {

    void archive(String taskId, String sessionId, String objectKeyHint);

    record ArchiveResult(String objectKey, boolean uploaded) {
    }
}
