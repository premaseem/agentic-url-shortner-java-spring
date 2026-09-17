package com.premaseem.shortener.web.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

import java.time.Instant;

public record CreateShortUrlRequest(

        @NotBlank(message = "originalUrl must not be blank")
        @URL(message = "originalUrl must be a valid URL")
        String originalUrl,

        String ownerName,

        Instant expiresAt) {
}
