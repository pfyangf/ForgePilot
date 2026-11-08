package com.forgepilot.exec.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 本机/开发进程运行时。容器化实现见 {@link ContainerRuntime}。 */
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
