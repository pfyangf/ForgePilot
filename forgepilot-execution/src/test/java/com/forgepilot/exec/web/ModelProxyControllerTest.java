package com.forgepilot.exec.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forgepilot.exec.model.InMemoryModelSessionProxy;
import com.forgepilot.exec.model.ModelSessionProxy;
import com.forgepilot.exec.model.UpstreamResponsesClient;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ModelProxyControllerTest {

    private MockMvc mockMvc;
    private InMemoryModelSessionProxy proxy;
    private AtomicReference<String> forwardedKey;

    @BeforeEach
    void setUp() {
        proxy = new InMemoryModelSessionProxy("127.0.0.1", 8081, 3600);
        forwardedKey = new AtomicReference<>();
        UpstreamResponsesClient upstream = (baseUrl, apiKey, body) -> {
            forwardedKey.set(apiKey);
            return new UpstreamResponsesClient.UpstreamResponse(
                    200, "{\"object\":\"response\",\"wire_api\":\"responses\",\"echo\":true}");
        };
        mockMvc = MockMvcBuilders.standaloneSetup(new ModelProxyController(proxy, upstream)).build();
    }

    @Test
    void stubWhenPlaceholderAndForwardWhenRealKeyWithoutLeaking() throws Exception {
        mockMvc.perform(post("/v1/responses").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        var stubCred = proxy.issue(new ModelSessionProxy.ModelSessionRequest(
                "sess-stub", "p", "m", "https://api.openai.com/v1", "PLACEHOLDER_KEY", "responses"));
        String stubBody = mockMvc.perform(post("/v1/responses")
                        .header("Authorization", "Bearer " + stubCred.shortLivedToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"m\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("stub"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(stubBody).doesNotContain("PLACEHOLDER_KEY");
        assertThat(forwardedKey.get()).isNull();

        var liveCred = proxy.issue(new ModelSessionProxy.ModelSessionRequest(
                "sess-live", "p", "m", "https://api.openai.com/v1", "sk-live-secret", "responses"));
        String liveBody = mockMvc.perform(post("/v1/responses")
                        .header("Authorization", "Bearer " + liveCred.shortLivedToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"m\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(forwardedKey.get()).isEqualTo("sk-live-secret");
        assertThat(liveBody).doesNotContain("sk-live-secret");
        assertThat(liveBody).contains("responses");

        mockMvc.perform(post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotImplemented());
    }
}
