package com.forgepilot.exec.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CodexCliAgentAdapterTest {

    @Test
    void codexIsTheOnlyMvpAdapterAndDoesNotDeclarePause() {
        var registry = new AgentAdapterRegistry(List.of(
                new CodexCliAgentAdapter(),
                new OpenHandsAgentServerAdapter(),
                new AcpAgentAdapter()));

        AgentAdapter mvp = registry.mvp();
        assertThat(mvp.kind()).isEqualTo(AgentAdapter.AgentKind.CODEX_CLI);
        assertThat(mvp.capabilities().nativePause()).isFalse();
        assertThat(mvp.capabilities().interactiveInput()).isFalse();
        assertThat(mvp.capabilities().interrupt()).isTrue();
        assertThat(mvp.capabilities().eventStream()).isTrue();
        assertThat(new OpenHandsAgentServerAdapter().maturity()).isEqualTo(AgentAdapter.AdapterMaturity.RESERVED);
        assertThat(new AcpAgentAdapter().maturity()).isEqualTo(AgentAdapter.AdapterMaturity.RESERVED);

        var context = new AgentAdapter.AgentExecutionContext(
                "task-1", "session-1", "/workspaces/task-1", "/workspaces/task-1/.agent-home",
                "http://127.0.0.1:8082/v1", "short-lived");
        assertThatThrownBy(() -> mvp.start(context)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(context.shortLivedModelToken()).isNotBlank();
    }
}
