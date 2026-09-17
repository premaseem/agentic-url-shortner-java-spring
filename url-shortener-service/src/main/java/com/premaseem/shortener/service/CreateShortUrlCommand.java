package com.premaseem.shortener.service;

import java.time.Instant;

public record CreateShortUrlCommand(String originalUrl, String ownerName, Instant expiresAt) {
}
