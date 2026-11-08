package com.forgepilot.exec.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 测试/本机假运行时：记录隔离标志，不启动 Docker。
 * 作为默认 {@link Primary}，保证单元测试无需 Docker daemon。
 */
@Primary
@Component
public class RecordingFakeContainerRuntime implements ContainerRuntime {

    private final List<StartSpec> started = new ArrayList<>();
    private final ConcurrentHashMap<String, StartSpec> live = new ConcurrentHashMap<>();

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public StartedContainer start(StartSpec spec) {
        validate(spec);
        started.add(spec);
        live.put(spec.containerRef(), spec);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("kind", spec.kind().name());
        record.put("defaultDenyEgress", spec.defaultDenyEgress());
        record.put("egressAllowlist", spec.egressAllowlist());
        record.put("denyTargets", spec.denyTargets());
        record.put("allowModelCredentials", spec.allowModelCredentials());
        record.put("allowAgentCli", spec.allowAgentCli());
        record.put("hasModelEnv", containsModelCred(spec.environment()));
        return new StartedContainer(spec.containerRef(), spec.kind(), record);
    }

    @Override
    public void destroy(String containerRef) {
        live.remove(containerRef);
    }

    public List<StartSpec> started() {
        return List.copyOf(started);
    }

    public void clear() {
        started.clear();
        live.clear();
    }

    private static void validate(StartSpec spec) {
        if (spec.kind() == SandboxKind.VERIFICATION) {
            if (spec.allowModelCredentials() || containsModelCred(spec.environment())) {
                throw new IllegalArgumentException("验证容器不得携带模型凭证");
            }
            if (spec.allowAgentCli()) {
                throw new IllegalArgumentException("验证容器不得包含 Agent CLI");
            }
        }
        if (!spec.defaultDenyEgress()) {
            throw new IllegalArgumentException("必须开启 default-deny egress");
        }
        for (String deny : ContainerRuntime.StartSpec.defaultDenyTargets()) {
            if (spec.denyTargets() == null || !spec.denyTargets().contains(deny)) {
                throw new IllegalArgumentException("缺少拒绝目标: " + deny);
            }
        }
    }

    private static boolean containsModelCred(Map<String, String> env) {
        if (env == null) {
            return false;
        }
        return env.keySet().stream()
                .anyMatch(k -> {
                    String key = k.toLowerCase();
                    return key.contains("api_key") || key.contains("openai") || key.contains("codex_api");
                });
    }
}
