package com.premaseem.shortener.web;

import com.premaseem.shortener.domain.ShortUrl;
import com.premaseem.shortener.service.CreateShortUrlCommand;
import com.premaseem.shortener.service.ShortUrlService;
import com.premaseem.shortener.web.dto.CreateShortUrlRequest;
import com.premaseem.shortener.web.dto.ShortUrlResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShortUrlController {

    private final ShortUrlService shortUrlService;

    public ShortUrlController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @PostMapping("/api/urls")
    public ResponseEntity<ShortUrlResponse> create(@Valid @RequestBody CreateShortUrlRequest request) {
        ShortUrl created = shortUrlService.createShortUrl(
                new CreateShortUrlCommand(request.originalUrl(), request.ownerName(), request.expiresAt()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        ShortUrl shortUrl = shortUrlService.resolveForRedirect(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, shortUrl.getOriginalUrl())
                .build();
    }

    private ShortUrlResponse toResponse(ShortUrl shortUrl) {
        return new ShortUrlResponse(
                shortUrl.getCode(),
                "/" + shortUrl.getCode(),
                shortUrl.getOriginalUrl(),
                shortUrl.getOwnerName(),
                shortUrl.getCreatedAt(),
                shortUrl.getExpiresAt());
    }
}
