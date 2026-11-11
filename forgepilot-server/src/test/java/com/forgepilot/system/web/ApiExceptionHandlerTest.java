package com.forgepilot.system.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new StubController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void responseStatusExceptionIncludesMessage() throws Exception {
        mvc.perform(get("/stub/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("任务不存在或无权查看"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @RestController
    static class StubController {
        @GetMapping("/stub/missing")
        public String missing() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在或无权查看");
        }
    }
}
