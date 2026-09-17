package com.premaseem.maestro.agent.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premaseem.maestro.retry.TransientStageException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Real {@link LlmClient} backed by the Anthropic Messages API. Network and
 * parsing failures are reported as {@link TransientStageException} so a
 * flaky call is retried by the engine rather than failing the run outright.
 *
 * Not exercised against the live API in this codebase's test suite (no key
 * is provisioned in CI/dev) — {@link LlmAgentStrategy}'s tests cover the
 * prompt/response contract against a stub {@link LlmClient} instead.
 */
public final class AnthropicLlmClient implements LlmClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String DEFAULT_MODEL = "claude-sonnet-5";
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AnthropicLlmClient(String apiKey, String model, HttpClient httpClient, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public static AnthropicLlmClient fromEnvironment() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not set");
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return new AnthropicLlmClient(apiKey, DEFAULT_MODEL, httpClient, new ObjectMapper());
    }

    @Override
    public String complete(String prompt) {
        try {
            Request requestBody = new Request(model, DEFAULT_MAX_TOKENS, List.of(new Message("user", prompt)));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new TransientStageException(
                        "Anthropic API returned status " + response.statusCode() + ": " + response.body());
            }

            Response parsed = objectMapper.readValue(response.body(), Response.class);
            if (parsed.content() == null || parsed.content().isEmpty()) {
                throw new TransientStageException("Anthropic API response had no content blocks");
            }
            return parsed.content().get(0).text();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TransientStageException("Anthropic API call failed: " + e.getMessage(), e);
        }
    }

    private record Request(String model, @JsonProperty("max_tokens") int maxTokens, List<Message> messages) {
    }

    private record Message(String role, String content) {
    }

    private record Response(List<ContentBlock> content) {
    }

    private record ContentBlock(String type, String text) {
    }
}
