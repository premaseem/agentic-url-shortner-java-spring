package com.premaseem.shortener.service;

/**
 * Strategy for producing candidate short codes. Swappable independently of
 * the persistence/retry logic in {@link ShortUrlService}.
 */
public interface ShortCodeGenerator {

    String generate();
}
