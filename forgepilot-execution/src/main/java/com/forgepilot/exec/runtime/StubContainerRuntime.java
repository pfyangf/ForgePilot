package com.forgepilot.exec.runtime;

import org.springframework.stereotype.Component;

/** Docker 尚未接通的占位。本地测试走 {@link LocalProcessRuntime}。 */
@Component
public class StubContainerRuntime implements ContainerRuntime {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public StartedContainer start(StartSpec spec) {
        throw new UnsupportedOperationException("ContainerRuntime 尚未实现；本机开发请用 ProcessRuntime");
    }

    @Override
    public void destroy(String containerRef) {
        // no-op
    }
}
