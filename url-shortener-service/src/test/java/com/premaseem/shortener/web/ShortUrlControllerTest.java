package com.premaseem.shortener.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.premaseem.shortener.domain.ShortUrl;
import com.premaseem.shortener.exception.ShortUrlExpiredException;
import com.premaseem.shortener.exception.ShortUrlNotFoundException;
import com.premaseem.shortener.service.ShortUrlService;
import com.premaseem.shortener.web.dto.CreateShortUrlRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShortUrlController.class)
class ShortUrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShortUrlService shortUrlService;

    @Test
    void create_happyPath_returns201WithBody() throws Exception {
        ShortUrl saved = ShortUrl.builder()
                .code("abc1234")
                .originalUrl("https://example.com")
                .ownerName("premaseem")
                .createdAt(Instant.parse("2026-09-17T10:00:00Z"))
                .clickCount(0L)
                .build();
        when(shortUrlService.createShortUrl(any())).thenReturn(saved);

        CreateShortUrlRequest request = new CreateShortUrlRequest("https://example.com", "premaseem", null);

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("abc1234"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"))
                .andExpect(jsonPath("$.ownerName").value("premaseem"));
    }

    @Test
    void create_blankOriginalUrl_returns400() throws Exception {
        CreateShortUrlRequest request = new CreateShortUrlRequest("", "premaseem", null);

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redirect_happyPath_returns302WithLocation() throws Exception {
        ShortUrl saved = ShortUrl.builder()
                .code("abc1234")
                .originalUrl("https://example.com")
                .clickCount(1L)
                .build();
        when(shortUrlService.resolveForRedirect(eq("abc1234"))).thenReturn(saved);

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    void redirect_unknownCode_returns404() throws Exception {
        when(shortUrlService.resolveForRedirect(eq("missing")))
                .thenThrow(new ShortUrlNotFoundException("missing"));

        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void redirect_expiredCode_returns410() throws Exception {
        when(shortUrlService.resolveForRedirect(eq("old0001")))
                .thenThrow(new ShortUrlExpiredException("old0001"));

        mockMvc.perform(get("/old0001"))
                .andExpect(status().isGone());
    }
}
