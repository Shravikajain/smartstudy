package com.smartstudy.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MlClientTest {

    @Test
    void fastApiErrorDetailIsShownInsteadOfRawJson() {
        assertThat(MlClient.detail("{\"detail\":\"The AI-writing detector is still loading.\"}", "fallback"))
                .isEqualTo("The AI-writing detector is still loading.");
        assertThat(MlClient.detail("{\"detail\":\"Say \\\"hi\\\"\"}", "fallback")).isEqualTo("Say \"hi\"");
    }

    @Test
    void validationErrorsAndEmptyBodiesUseTheFallbackMessage() {
        assertThat(MlClient.detail("{\"detail\":[{\"loc\":[\"body\"]}]}", "fallback")).isEqualTo("fallback");
        assertThat(MlClient.detail("", "fallback")).isEqualTo("fallback");
        assertThat(MlClient.detail(null, "fallback")).isEqualTo("fallback");
    }
}
