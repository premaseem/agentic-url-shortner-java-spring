package com.premaseem.shortener.service;

import com.premaseem.shortener.domain.ShortUrl;

public interface ShortUrlService {

    ShortUrl createShortUrl(CreateShortUrlCommand command);

    ShortUrl resolveForRedirect(String code);
}
