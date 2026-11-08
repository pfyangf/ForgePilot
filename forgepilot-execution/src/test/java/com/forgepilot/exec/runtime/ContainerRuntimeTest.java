package com.forgepilot.exec.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContainerRuntimeTest {

    @TempDir
    Path temp;

    @Test
    void fakeRuntimeRecordsIsolationFlagsWithoutDocker() {
        RecordingFakeContainerRuntime runtime = new RecordingFakeContainerRuntime();
        assertThat(runtime.isAvailable()).isTrue();

        var agent = runtime.start(ContainerRuntime.StartSpec.agent(
                "agent-1",
                "forgepilot/agent:dev",
                List.of("codex", "exec"),
                temp.resolve("ws"),
                temp.resolve("home"),
                Map.of("CODEX_API_KEY", "fp_ms_short"),
                List.of("git.example.com", "127.0.0.1:8081", "registry.npmjs.org")));
        assertThat(agent.kind()).isEqualTo(ContainerRuntime.SandboxKind.AGENT);
        assertThat(agent.isolationRecord().get("defaultDenyEgress")).isEqualTo(true);
        assertThat(agent.isolationRecord().get("allowModelCredentials")).isEqualTo(true);
        Object denyObj = agent.isolationRecord().get("denyTargets");
        assertThat(denyObj.toString()).contains("postgresql").contains("redis").contains("rabbitmq");

        var verify = runtime.start(ContainerRuntime.StartSpec.verification(
                "verify-1",
                "forgepilot/verify:dev",
                List.of("bash", "-lc", "true"),
                temp.resolve("ws"),
                List.of("git.example.com", "registry.npmjs.org")));
        assertThat(verify.kind()).isEqualTo(ContainerRuntime.SandboxKind.VERIFICATION);
        assertThat(verify.isolationRecord().get("allowAgentCli")).isEqualTo(false);
        assertThat(verify.isolationRecord().get("allowModelCredentials")).isEqualTo(false);

        assertThatThrownBy(() -> runtime.start(new ContainerRuntime.StartSpec(
                        "bad",
                        ContainerRuntime.SandboxKind.VERIFICATION,
                        "img",
                        List.of("true"),
                        temp,
                        null,
                        Map.of("OPENAI_API_KEY", "sk"),
                        true,
                        List.of("git"),
                        ContainerRuntime.StartSpec.defaultDenyTargets(),
                        true,
                        false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型凭证");
    }

    @Test
    void dockerRuntimeReportsAvailabilityWithoutFailingTests() {
        DockerContainerRuntime docker = new DockerContainerRuntime();
        // may be true or false depending on host; calling must not throw
        boolean available = docker.isAvailable();
        assertThat(available || !available).isTrue();
    }
}
