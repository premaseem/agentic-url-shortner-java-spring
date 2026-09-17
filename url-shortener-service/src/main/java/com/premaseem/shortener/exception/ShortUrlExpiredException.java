package com.premaseem.shortener.exception;

public class ShortUrlExpiredException extends RuntimeException {

    public ShortUrlExpiredException(String code) {
        super("Short URL has expired for code: " + code);
    }
}
