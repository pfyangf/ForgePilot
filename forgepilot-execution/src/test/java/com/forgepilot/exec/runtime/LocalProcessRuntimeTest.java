package com.forgepilot.exec.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalProcessRuntimeTest {

    @TempDir
    Path temp;

    @Test
    void stripsInheritedProviderApiKeyAndRejectsLongLivedSecret() {
        Map<String, String> env = new HashMap<>();
        env.put("OPENAI_API_KEY", "sk-live-secret");
        env.put("CODEX_API_KEY", "fp_ms_short");
        env.put("PATH", "/usr/bin");
        LocalProcessRuntime.stripSensitive(env);
        assertThat(env).doesNotContainKey("OPENAI_API_KEY");
        assertThat(env).containsEntry("CODEX_API_KEY", "fp_ms_short");

        LocalProcessRuntime runtime = new LocalProcessRuntime();
        assertThatThrownBy(() -> runtime.start(new ProcessRuntime.StartSpec(
                        "h1",
                        List.of("true"),
                        temp,
                        Map.of("OPENAI_API_KEY", "sk-live"),
                        temp.resolve("out.log"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void doesNotHangWaitingForStdin() throws Exception {
        Path log = temp.resolve("stdin.log");
        LocalProcessRuntime runtime = new LocalProcessRuntime();
        var handle = runtime.start(new ProcessRuntime.StartSpec(
                "h-stdin",
                List.of("bash", "-lc", "if IFS= read -r -t 1 line; then echo GOT; else echo EOF; fi"),
                temp,
                Map.of(),
                log));
        assertThat(handle.pid()).isPositive();
        ProcessHandle.of(handle.pid()).ifPresent(ph -> {
            try {
                ph.onExit().get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        String out = Files.readString(log, StandardCharsets.UTF_8);
        assertThat(out).contains("EOF");
        assertThat(out).doesNotContain("GOT");
    }

    @Test
    void requireSandboxFailsClosedWithoutStartingProcess() {
        LocalProcessRuntime rt = LocalProcessRuntime.requiringSandbox();
        assertThatThrownBy(() -> rt.start(new ProcessRuntime.StartSpec(
                        "h2", List.of("true"), temp, Map.of("CODEX_API_KEY", "fp_ms"), temp.resolve("o.log"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("require-sandbox");
    }
}
