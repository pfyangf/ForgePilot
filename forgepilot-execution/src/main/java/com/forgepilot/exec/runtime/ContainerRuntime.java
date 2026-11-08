package com.forgepilot.exec.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 容器运行时端口。
 * <ul>
 *   <li>Agent 容器与验证容器分离</li>
 *   <li>验证容器不得有 Agent CLI，也不得有模型凭证</li>
 *   <li>默认拒绝出站，仅放行 Git 主机、模型代理、包仓库</li>
 *   <li>Agent 不得访问控制面 / PostgreSQL / Redis / RabbitMQ / MinIO 管理 / 云元数据</li>
 * </ul>
 * {@code mvn test} 不要求 Docker：使用 {@link RecordingFakeContainerRuntime}。
 */
public interface ContainerRuntime {

    enum SandboxKind {
        AGENT,
        VERIFICATION
    }

    boolean isAvailable();

    StartedContainer start(StartSpec spec);

    void destroy(String containerRef);

    record StartSpec(
            String containerRef,
            SandboxKind kind,
            String image,
            List<String> command,
            Path workspaceBind,
            Path agentHomeBind,
            Map<String, String> environment,
            boolean defaultDenyEgress,
            List<String> egressAllowlist,
            List<String> denyTargets,
            boolean allowModelCredentials,
            boolean allowAgentCli) {

        public static StartSpec agent(
                String ref,
                String image,
                List<String> command,
                Path workspace,
                Path agentHome,
                Map<String, String> env,
                List<String> allowlist) {
            return new StartSpec(
                    ref,
                    SandboxKind.AGENT,
                    image,
                    command,
                    workspace,
                    agentHome,
                    env,
                    true,
                    allowlist,
                    defaultDenyTargets(),
                    true,
                    true);
        }

        public static StartSpec verification(
                String ref, String image, List<String> command, Path workspace, List<String> allowlist) {
            return new StartSpec(
                    ref,
                    SandboxKind.VERIFICATION,
                    image,
                    command,
                    workspace,
                    null,
                    Map.of(),
                    true,
                    allowlist,
                    defaultDenyTargets(),
                    false,
                    false);
        }

        public static List<String> defaultDenyTargets() {
            return List.of(
                    "control-plane",
                    "postgresql",
                    "redis",
                    "rabbitmq",
                    "minio-admin",
                    "169.254.169.254",
                    "metadata.google.internal");
        }
    }

    record StartedContainer(String containerRef, SandboxKind kind, Map<String, Object> isolationRecord) {
    }
}
