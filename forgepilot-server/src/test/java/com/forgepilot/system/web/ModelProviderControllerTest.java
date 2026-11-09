package com.forgepilot.system.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgepilot.system.service.ModelProviderCatalogService.ModelCode;
import com.forgepilot.system.service.ModelProviderCatalogService.ProviderView;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelProviderControllerTest {

    @Test
    void providerViewContractNeverCarriesSecretFields() {
        ProviderView view = new ProviderView(
                "p1",
                "t1",
                "local",
                "OPENAI_COMPAT",
                "http://127.0.0.1/v1",
                true,
                null,
                "responses",
                null,
                true,
                false,
                true,
                false,
                List.of(new ModelCode("gpt-4o", "GPT-4o")));
        assertThat(view.hasSecret()).isTrue();
        assertThat(view.models()).hasSize(1);
        assertThat(view.getClass().getRecordComponents())
                .extracting(c -> c.getName())
                .doesNotContain("apiKey", "secretId", "ciphertext", "password")
                .contains("models");
        assertThat(view.toString()).doesNotContain("sk-").doesNotContain("ciphertext");
    }
}
