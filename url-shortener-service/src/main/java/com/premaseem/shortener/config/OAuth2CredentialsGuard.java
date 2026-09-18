package com.premaseem.shortener.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Explicit fail-fast check for the "oauth2" profile. Spring's own
 * {@code ${GOOGLE_CLIENT_ID}} placeholder resolution does NOT throw when
 * the env var is unset -- it silently falls back to the literal
 * unresolved text as the value (confirmed by testing: the app boots fine
 * and sends "${GOOGLE_CLIENT_ID}" to Google as the actual client id).
 * Relying on that would be a confusing runtime failure instead of a clear
 * one, so this validates explicitly instead.
 */
@Component
@Profile("oauth2")
public class OAuth2CredentialsGuard {

    @PostConstruct
    public void requireGoogleCredentials() {
        validate(System.getenv("GOOGLE_CLIENT_ID"), System.getenv("GOOGLE_CLIENT_SECRET"));
    }

    static void validate(String clientId, String clientSecret) {
        requireNonBlank(clientId, "GOOGLE_CLIENT_ID");
        requireNonBlank(clientSecret, "GOOGLE_CLIENT_SECRET");
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "The 'oauth2' profile is active but " + name + " is not set. Provide real Google "
                            + "OAuth credentials, or drop --spring.profiles.active=oauth2 to run without login.");
        }
    }
}
