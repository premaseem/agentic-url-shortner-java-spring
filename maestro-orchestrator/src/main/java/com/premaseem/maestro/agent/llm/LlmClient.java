package com.premaseem.maestro.agent.llm;

/**
 * Seam between {@link LlmAgentStrategy} and an actual model provider, so the
 * strategy's prompt-building and response-parsing logic is unit-testable
 * without a network call. {@link AnthropicLlmClient} is the real
 * implementation.
 */
@FunctionalInterface
public interface LlmClient {

    String complete(String prompt);
}
