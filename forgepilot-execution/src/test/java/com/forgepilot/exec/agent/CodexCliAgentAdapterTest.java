package com.forgepilot.exec.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgepilot.exec.log.SessionLogStore;
import com.forgepilot.exec.runtime.LocalProcessRuntime;
import com.forgepilot.exec.runtime.ProcessRuntime;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodexCliAgentAdapterTest {

    @TempDir
    Path temp;

    @Test
    void codexIsTheOnlyMvpAdapterAndDoesNotDeclarePause() {
        ProcessRuntime runtime = new LocalProcessRuntime();
        SessionLogStore logs = new SessionLogStore(temp.toString());
        var registry = new AgentAdapterRegistry(List.of(
                new CodexCliAgentAdapter(runtime, logs),
                new OpenHandsAgentServerAdapter(),
                new AcpAgentAdapter()));

        AgentAdapter mvp = registry.mvp();
        assertThat(mvp.kind()).isEqualTo(AgentAdapter.AgentKind.CODEX_CLI);
        assertThat(mvp.maturity()).isEqualTo(AgentAdapter.AdapterMaturity.MVP);
        assertThat(mvp.capabilities().nativePause()).isFalse();
        assertThat(mvp.capabilities().interactiveInput()).isFalse();
        assertThat(mvp.capabilities().interrupt()).isTrue();
        assertThat(new OpenHandsAgentServerAdapter().maturity()).isEqualTo(AgentAdapter.AdapterMaturity.RESERVED);
        assertThat(new AcpAgentAdapter().maturity()).isEqualTo(AgentAdapter.AdapterMaturity.RESERVED);
    }

    @Test
    void startFailsCleanlyWhenCodexMissingAndNeverMarksSuccess() {
        ProcessRuntime missing = new ProcessRuntime() {
            @Override
            public boolean isCommandAvailable(String command) {
                return false;
            }

            @Override
            public StartedHandle start(StartSpec spec) {
                throw new AssertionError("must not start");
            }

            @Override
            public void destroy(String handleId) {
            }

            @Override
            public boolean isAlive(String handleId) {
                return false;
            }
        };
        CodexCliAgentAdapter adapter = new CodexCliAgentAdapter(missing, new SessionLogStore(temp.toString()));
        var context = new AgentAdapter.AgentExecutionContext(
                "task-1",
                "session-1",
                temp.resolve("ws").toString(),
                temp.resolve("home").toString(),
                "http://127.0.0.1:19081/v1",
                "fp_ms_test",
                "model-x");
        assertThatThrownBy(() -> adapter.start(context))
                .isInstanceOf(AgentNotAvailableException.class)
                .hasMessageContaining("NOT_AVAILABLE");
    }

    @Test
    void writesResponsesWireApiConfigWithoutProviderSecret() throws Exception {
        ProcessRuntime capturing = new ProcessRuntime() {
            StartSpec last;

            @Override
            public boolean isCommandAvailable(String command) {
                return true;
            }

            @Override
            public StartedHandle start(StartSpec spec) {
                this.last = spec;
                assertThat(spec.environment().get("CODEX_API_KEY")).isEqualTo("fp_ms_short");
                assertThat(spec.environment().toString()).doesNotContain("sk-provider");
                return new StartedHandle(spec.handleId(), 1L);
            }

            @Override
            public void destroy(String handleId) {
            }

            @Override
            public boolean isAlive(String handleId) {
                return false;
            }
        };
        CodexCliAgentAdapter adapter = new CodexCliAgentAdapter(capturing, new SessionLogStore(temp.toString()));
        var context = new AgentAdapter.AgentExecutionContext(
                "task-1",
                "session-2",
                temp.resolve("ws").toString(),
                temp.resolve("home").toString(),
                "http://127.0.0.1:19081/v1",
                "fp_ms_short",
                "gpt-test");
        adapter.start(context);
        String toml = java.nio.file.Files.readString(temp.resolve("home/config.toml"));
        assertThat(toml).contains("wire_api = \"responses\"");
        assertThat(toml).contains("base_url = \"http://127.0.0.1:19081/v1\"");
        assertThat(toml).doesNotContain("sk-provider");
        assertThat(toml.toLowerCase()).doesNotContain("api_key=");
    }

    @Test
    void doesNotPassConfigFilePathAndPassesPrompt() throws Exception {
        RecordingRuntime recording = new RecordingRuntime();
        CodexCliAgentAdapter adapter = new CodexCliAgentAdapter(recording, new SessionLogStore(temp.toString()));
        String prompt = "实现九九乘法表页面，并保证算式正确。";
        var context = new AgentAdapter.AgentExecutionContext(
                "task-1",
                "session-3",
                temp.resolve("ws").toString(),
                temp.resolve("home").toString(),
                "http://127.0.0.1:19081/v1",
                "fp_ms_short",
                "gpt-test",
                prompt);
        adapter.start(context);

        assertThat(recording.last).isNotNull();
        assertThat(recording.last.command()).containsExactly("codex", "exec", prompt);
        assertThat(recording.last.command()).doesNotContain("--config").doesNotContain("-c");
        assertThat(recording.last.environment().get("CODEX_HOME")).isEqualTo(temp.resolve("home").toString());
        String toml = java.nio.file.Files.readString(temp.resolve("home/config.toml"));
        assertThat(toml).contains("wire_api = \"responses\"");
        assertThat(toml).contains("base_url = \"http://127.0.0.1:19081/v1\"");
    }

    private static final class RecordingRuntime implements ProcessRuntime {
        StartSpec last;

        @Override
        public boolean isCommandAvailable(String command) {
            return true;
        }

        @Override
        public StartedHandle start(StartSpec spec) {
            this.last = spec;
            return new StartedHandle(spec.handleId(), 1L);
        }

        @Override
        public void destroy(String handleId) {
        }

        @Override
        public boolean isAlive(String handleId) {
            return false;
        }
    }
}
