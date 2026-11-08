package com.forgepilot.exec.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 容器运行时端口。MVP 本机测试不要求真实 Docker；后续 Agent / 验证容器由此接入。
 * 默认拒绝：Agent 不得拿到控制面、PostgreSQL、Redis、RabbitMQ、MinIO 管理凭证；出口默认拒绝（由网络策略实现）。
 */
public interface ContainerRuntime {

    boolean isAvailable();

    StartedContainer start(StartSpec spec);

    void destroy(String containerRef);

    record StartSpec(
            String containerRef,
            String image,
            List<String> command,
            Path workspaceBind,
            Path agentHomeBind,
            Map<String, String> environment,
            boolean defaultDenyEgress) {
    }

    record StartedContainer(String containerRef) {
    }
}
