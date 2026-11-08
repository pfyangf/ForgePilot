package com.forgepilot.exec.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgepilot.exec.model.InMemoryModelSessionProxy;
import com.forgepilot.exec.model.ModelSessionProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ModelProxyControllerTest {

    private MockMvc mockMvc;
    private InMemoryModelSessionProxy proxy;

    @BeforeEach
    void setUp() {
        proxy = new InMemoryModelSessionProxy("127.0.0.1", 8081, 3600);
        mockMvc = MockMvcBuilders.standaloneSetup(new ModelProxyController(proxy, new ObjectMapper())).build();
    }

    @Test
    void responsesRequiresTokenAndRejectsChatCompletions() throws Exception {
        mockMvc.perform(post("/v1/responses").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        var cred = proxy.issue(new ModelSessionProxy.ModelSessionRequest(
                "sess", "p", "m", null, null, "responses"));
        mockMvc.perform(post("/v1/responses")
                        .header("Authorization", "Bearer " + cred.shortLivedToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"m\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wire_api").value("responses"));

        mockMvc.perform(post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotImplemented());

        proxy.revoke("sess");
        mockMvc.perform(post("/v1/responses")
                        .header("Authorization", "Bearer " + cred.shortLivedToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        assertThat(cred.shortLivedToken()).doesNotContain("PLACEHOLDER");
    }
}
