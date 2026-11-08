package com.forgepilot.exec.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 本机/开发进程运行时。容器化实现见 {@link ContainerRuntime}。
 * <p>
 * 进程模式<strong>不是</strong>生产隔离：子进程与控制面同主机同用户命名空间。
 * 生产请使用 ContainerRuntime（默认 network=none）。
 */
public interface ProcessRuntime {

    boolean isCommandAvailable(String command);

    StartedHandle start(StartSpec spec);

    void destroy(String handleId);

    boolean isAlive(String handleId);

    record StartSpec(
            String handleId,
            List<String> command,
            Path workingDirectory,
            Map<String, String> environment,
            Path stdoutLog) {
    }

    record StartedHandle(String handleId, long pid) {
    }
}
