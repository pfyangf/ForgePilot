package com.forgepilot.system.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ModelProviderControllerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ModelProviderController()).build();

    @Test
    void catalogRoutesAreNotImplementedAndDoNotReturnSecrets() throws Exception {
        var body = mvc.perform(get("/api/v1/model-providers"))
                .andExpect(status().isNotImplemented())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("apiKey").doesNotContain("sk-");
        mvc.perform(delete("/api/v1/model-providers")).andExpect(status().isNotImplemented());
    }
}
