package com.forgepilot.exec;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgepilot.exec.agent.AgentAdapter;
import com.forgepilot.exec.agent.AgentAdapterRegistry;
import com.forgepilot.exec.model.ModelSessionProxy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExecutionHealthTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AgentAdapterRegistry registry;

    @Autowired
    private ModelSessionProxy modelSessionProxy;

    @Test
    void healthAndAdapterWiring() {
        ResponseEntity<String> health = rest.getForEntity("/api/v1/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("forgepilot-execution");
        assertThat(registry.mvp().kind()).isEqualTo(AgentAdapter.AgentKind.CODEX_CLI);
        assertThat(modelSessionProxy.exposedWireApi()).isEqualTo("responses");
    }
}
