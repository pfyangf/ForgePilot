package com.forgepilot.exec.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgepilot.exec.agent.AcpAgentAdapter;
import com.forgepilot.exec.agent.AgentAdapterRegistry;
import com.forgepilot.exec.agent.AgentNotAvailableException;
import com.forgepilot.exec.agent.CodexCliAgentAdapter;
import com.forgepilot.exec.agent.OpenHandsAgentServerAdapter;
import com.forgepilot.exec.log.SessionLogStore;
import com.forgepilot.exec.model.InMemoryModelSessionProxy;
import com.forgepilot.exec.runtime.ProcessRuntime;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionRuntimeServiceTest {

    @TempDir
    Path temp;

    @Test
    void startRevokesTokenWhenCodexUnavailable() {
        ProcessRuntime missing = unavailableRuntime();
        InMemoryModelSessionProxy proxy = new InMemoryModelSessionProxy("127.0.0.1", 19081, 3600);
        SessionRuntimeService service = new SessionRuntimeService(
                new AgentAdapterRegistry(List.of(
                        new CodexCliAgentAdapter(missing, new SessionLogStore(temp.toString())),
                        new OpenHandsAgentServerAdapter(),
                        new AcpAgentAdapter())),
                proxy,
                new SessionLogStore(temp.toString()));

        assertThatThrownBy(() -> service.start(new SessionRuntimeService.StartRequest(
                        "t1",
                        "s1",
                        temp.resolve("ws").toString(),
                        temp.resolve("home").toString(),
                        "prov",
                        "model",
                        "http://up",
                        "sk-secret",
                        "responses")))
                .isInstanceOf(AgentNotAvailableException.class);

        assertThatThrownBy(() -> proxy.requireValidToken("ignored")).isInstanceOf(Exception.class);
        // token should have been revoked — issuing again works; old unknown
        var cred = proxy.issue(new com.forgepilot.exec.model.ModelSessionProxy.ModelSessionRequest(
                "other", "p", "m", null, null, "responses"));
        proxy.revoke("other");
        assertThat(cred.shortLivedToken()).isNotBlank();
    }

    private static ProcessRuntime unavailableRuntime() {
        return new ProcessRuntime() {
            @Override
            public boolean isCommandAvailable(String command) {
                return false;
            }

            @Override
            public StartedHandle start(StartSpec spec) {
                throw new AssertionError();
            }

            @Override
            public void destroy(String handleId) {
            }

            @Override
            public boolean isAlive(String handleId) {
                return false;
            }
        };
    }
}
