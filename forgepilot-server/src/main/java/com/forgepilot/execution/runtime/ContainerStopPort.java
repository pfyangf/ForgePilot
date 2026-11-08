package com.forgepilot.execution.runtime;

/** 容器停止端口。实现不得删除任务工作区。 */
public interface ContainerStopPort {

    void stop(String taskId, String containerRef);
}
