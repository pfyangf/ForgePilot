package com.forgepilot.exec.collector;

/** 把日志、Diff 和快照上传到对象存储。Agent 容器不直接上传。 */
public interface ArtifactCollector {

    void upload(String taskId, String sessionId);
}
