package com.premaseem.shortener.service;

import com.premaseem.shortener.domain.ShortUrl;
import com.premaseem.shortener.exception.ShortUrlExpiredException;
import com.premaseem.shortener.exception.ShortUrlNotFoundException;
import com.premaseem.shortener.repository.ShortUrlRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class ShortUrlServiceImpl implements ShortUrlService {

    private static final int MAX_CODE_GENERATION_ATTEMPTS = 5;

    private final ShortUrlRepository repository;
    private final ShortCodeGenerator codeGenerator;
    private final Clock clock;

    public ShortUrlServiceImpl(ShortUrlRepository repository, ShortCodeGenerator codeGenerator, Clock clock) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    @Override
    public ShortUrl createShortUrl(CreateShortUrlCommand command) {
        ShortUrl shortUrl = ShortUrl.builder()
                .code(generateUniqueCode())
                .originalUrl(command.originalUrl())
                .ownerName(command.ownerName())
                .createdAt(clock.instant())
                .expiresAt(command.expiresAt())
                .clickCount(0L)
                .build();
        return repository.save(shortUrl);
    }

    @Override
    public ShortUrl resolveForRedirect(String code) {
        ShortUrl shortUrl = repository.findByCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));

        if (shortUrl.isExpired(clock.instant())) {
            throw new ShortUrlExpiredException(code);
        }

        shortUrl.recordClick();
        return repository.save(shortUrl);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String candidate = codeGenerator.generate();
            if (!repository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Unable to generate a unique short code after " + MAX_CODE_GENERATION_ATTEMPTS + " attempts");
    }
}
