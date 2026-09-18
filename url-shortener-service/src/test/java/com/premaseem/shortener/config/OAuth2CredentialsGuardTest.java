package com.premaseem.shortener.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2CredentialsGuardTest {

    @Test
    void passesWhenBothCredentialsArePresent() {
        assertThatCode(() -> OAuth2CredentialsGuard.validate("real-client-id", "real-client-secret"))
                .doesNotThrowAnyException();
    }

    @Test
    void failsWhenClientIdIsMissing() {
        assertThatThrownBy(() -> OAuth2CredentialsGuard.validate(null, "real-client-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_CLIENT_ID");
    }

    @Test
    void failsWhenClientSecretIsBlank() {
        assertThatThrownBy(() -> OAuth2CredentialsGuard.validate("real-client-id", "   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_CLIENT_SECRET");
    }
}
