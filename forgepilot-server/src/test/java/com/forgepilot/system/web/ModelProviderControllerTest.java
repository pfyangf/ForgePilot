package com.forgepilot.system.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgepilot.system.service.ModelProviderCatalogService.ProviderView;
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
                false);
        assertThat(view.hasSecret()).isTrue();
        assertThat(view.getClass().getRecordComponents())
                .extracting(c -> c.getName())
                .doesNotContain("apiKey", "secretId", "ciphertext", "password");
        assertThat(view.toString()).doesNotContain("sk-").doesNotContain("ciphertext");
    }
}
