package com.premaseem.shortener.web.dto;

import java.time.Instant;

public record ShortUrlResponse(
        String code,
        String shortUrl,
        String originalUrl,
        String ownerName,
        Instant createdAt,
        Instant expiresAt) {
}
