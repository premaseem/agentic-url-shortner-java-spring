package com.premaseem.shortener.service;

import com.premaseem.shortener.domain.ShortUrl;
import com.premaseem.shortener.exception.ShortUrlExpiredException;
import com.premaseem.shortener.exception.ShortUrlNotFoundException;
import com.premaseem.shortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceImplTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-09-17T10:00:00Z");

    @Mock
    private ShortUrlRepository repository;

    @Mock
    private ShortCodeGenerator codeGenerator;

    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private ShortUrlServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShortUrlServiceImpl(repository, codeGenerator, clock);
    }

    @Test
    void createShortUrl_happyPath_savesGeneratedCode() {
        when(codeGenerator.generate()).thenReturn("abc1234");
        when(repository.existsByCode("abc1234")).thenReturn(false);
        when(repository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateShortUrlCommand command = new CreateShortUrlCommand("https://example.com", "premaseem", null);

        ShortUrl result = service.createShortUrl(command);

        assertThat(result.getCode()).isEqualTo("abc1234");
        assertThat(result.getOriginalUrl()).isEqualTo("https://example.com");
        assertThat(result.getOwnerName()).isEqualTo("premaseem");
        assertThat(result.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(result.getClickCount()).isZero();
    }

    @Test
    void createShortUrl_retriesOnCodeCollisionUntilUnique() {
        when(codeGenerator.generate()).thenReturn("dup0001", "dup0001", "uniq001");
        when(repository.existsByCode("dup0001")).thenReturn(true);
        when(repository.existsByCode("uniq001")).thenReturn(false);
        when(repository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrl result = service.createShortUrl(
                new CreateShortUrlCommand("https://example.com", "premaseem", null));

        assertThat(result.getCode()).isEqualTo("uniq001");
        verify(codeGenerator, times(3)).generate();
    }

    @Test
    void createShortUrl_givesUpAfterMaxAttemptsOnPersistentCollisions() {
        when(codeGenerator.generate()).thenReturn("dup0001");
        when(repository.existsByCode("dup0001")).thenReturn(true);

        CreateShortUrlCommand command = new CreateShortUrlCommand("https://example.com", "premaseem", null);

        assertThatThrownBy(() -> service.createShortUrl(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to generate a unique short code");

        verify(repository, never()).save(any());
    }

    @Test
    void resolveForRedirect_happyPath_incrementsClickCount() {
        ShortUrl stored = ShortUrl.builder()
                .id(1L)
                .code("abc1234")
                .originalUrl("https://example.com")
                .ownerName("premaseem")
                .createdAt(FIXED_NOW.minusSeconds(60))
                .clickCount(4L)
                .build();
        when(repository.findByCode("abc1234")).thenReturn(Optional.of(stored));
        when(repository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrl result = service.resolveForRedirect("abc1234");

        assertThat(result.getClickCount()).isEqualTo(5L);
        assertThat(result.getOriginalUrl()).isEqualTo("https://example.com");
    }

    @Test
    void resolveForRedirect_unknownCode_throwsNotFound() {
        when(repository.findByCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveForRedirect("missing"))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }

    @Test
    void resolveForRedirect_expiredCode_throwsExpiredAndDoesNotSave() {
        ShortUrl expired = ShortUrl.builder()
                .id(2L)
                .code("old0001")
                .originalUrl("https://example.com")
                .createdAt(FIXED_NOW.minusSeconds(120))
                .expiresAt(FIXED_NOW.minusSeconds(1))
                .clickCount(0L)
                .build();
        when(repository.findByCode("old0001")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.resolveForRedirect("old0001"))
                .isInstanceOf(ShortUrlExpiredException.class);

        verify(repository, never()).save(any());
    }
}
