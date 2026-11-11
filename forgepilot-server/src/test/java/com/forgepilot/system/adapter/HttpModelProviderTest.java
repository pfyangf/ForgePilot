package com.forgepilot.system.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpModelProviderTest {

    @Test
    void extractTextReadsChatCompletionsAndResponses() throws Exception {
        assertThat(HttpModelProvider.extractText(
                        "{\"choices\":[{\"message\":{\"content\":\"审核意见正文\"}}]}"))
                .isEqualTo("审核意见正文");
        assertThat(HttpModelProvider.extractText("{\"output_text\":\"发布说明\"}")).isEqualTo("发布说明");
        assertThat(HttpModelProvider.extractText(
                        "{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"第一段\"},{\"text\":\"第二段\"}]}]}"))
                .isEqualTo("第一段\n第二段");
    }
}
