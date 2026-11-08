package com.forgepilot.exec.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void requireSandboxFailsClosedWithoutStartingProcess() {
        LocalProcessRuntime rt = LocalProcessRuntime.requiringSandbox();
        assertThatThrownBy(() -> rt.start(new ProcessRuntime.StartSpec(
                        "h2", List.of("true"), temp, Map.of("CODEX_API_KEY", "fp_ms"), temp.resolve("o.log"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("require-sandbox");
    }
}
