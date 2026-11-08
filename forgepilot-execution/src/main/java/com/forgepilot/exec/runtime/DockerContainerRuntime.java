package com.forgepilot.exec.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * 可选 Docker 实现。仅当本机 {@code docker} 可用时 {@link #isAvailable()} 为 true。
 * 单元测试默认走 {@link RecordingFakeContainerRuntime}，不要求 Docker daemon。
 */
@Component
public class DockerContainerRuntime implements ContainerRuntime {

    private final boolean allowlistNetworkEnabled;
    private final String allowlistNetworkName;

    public DockerContainerRuntime(
            @org.springframework.beans.factory.annotation.Value("${forgepilot.docker.allowlist-network.enabled:false}")
                    boolean allowlistNetworkEnabled,
            @org.springframework.beans.factory.annotation.Value("${forgepilot.docker.allowlist-network.name:forgepilot-egress}")
                    String allowlistNetworkName) {
        this.allowlistNetworkEnabled = allowlistNetworkEnabled;
        this.allowlistNetworkName = allowlistNetworkName;
    }

    /** 测试用：无 Spring 时默认安全模式 network=none */
    public DockerContainerRuntime() {
        this(false, "forgepilot-egress");
    }

    @Override
    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "info").redirectErrorStream(true).start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public StartedContainer start(StartSpec spec) {
        if (!isAvailable()) {
            throw new IllegalStateException("Docker 不可用");
        }
        if (spec.kind() == SandboxKind.VERIFICATION
                && (spec.allowModelCredentials() || spec.allowAgentCli())) {
            throw new IllegalArgumentException("验证容器隔离违规");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("-d");
        cmd.add("--name");
        cmd.add(spec.containerRef());
        cmd.add("--network");
        String network = resolveNetwork(spec);
        cmd.add(network);
        // 默认 network=none（安全）。allowlist 模式需预先创建自定义 bridge，并由宿主防火墙放行 Git/代理/包仓库。
        if (spec.workspaceBind() != null) {
            cmd.add("-v");
            cmd.add(spec.workspaceBind() + ":/workspace:rw");
        }
        if (spec.agentHomeBind() != null && spec.kind() == SandboxKind.AGENT) {
            cmd.add("-v");
            cmd.add(spec.agentHomeBind() + ":/agent-home:rw");
        }
        if (spec.environment() != null) {
            for (Map.Entry<String, String> e : spec.environment().entrySet()) {
                if (spec.kind() == SandboxKind.VERIFICATION
                        && (e.getKey().toLowerCase().contains("api_key")
                                || e.getKey().toLowerCase().contains("codex"))) {
                    continue;
                }
                cmd.add("-e");
                cmd.add(e.getKey() + "=" + e.getValue());
            }
        }
        cmd.add(spec.image());
        if (spec.command() != null) {
            cmd.addAll(spec.command());
        }
        try {
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished || process.exitValue() != 0) {
                throw new IllegalStateException("docker run 失败: " + out);
            }
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("kind", spec.kind().name());
            record.put("defaultDenyEgress", spec.defaultDenyEgress());
            record.put("egressAllowlist", spec.egressAllowlist());
            record.put("denyTargets", spec.denyTargets());
            record.put("docker", true);
            record.put("network", network);
            record.put("allowlistNetworkEnabled", allowlistNetworkEnabled);
            record.put("egressAllowlist", spec.egressAllowlist());
            return new StartedContainer(spec.containerRef(), spec.kind(), record);
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("docker run 异常", ex);
        }
    }

    String resolveNetwork(StartSpec spec) {
        if (!spec.defaultDenyEgress()) {
            return "bridge";
        }
        if (allowlistNetworkEnabled) {
            return allowlistNetworkName;
        }
        return "none";
    }

    @Override
    public void destroy(String containerRef) {
        try {
            new ProcessBuilder("docker", "rm", "-f", containerRef).start().waitFor(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }
}
