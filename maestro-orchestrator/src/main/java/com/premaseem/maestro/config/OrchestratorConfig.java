package com.premaseem.maestro.config;

import com.premaseem.maestro.agent.AgentStrategy;
import com.premaseem.maestro.agent.DeterministicAgentStrategy;
import com.premaseem.maestro.agent.llm.AnthropicLlmClient;
import com.premaseem.maestro.agent.llm.LlmAgentStrategy;
import com.premaseem.maestro.orchestrator.WorkflowOrchestrator;
import com.premaseem.maestro.policy.DiffSizeGuardrail;
import com.premaseem.maestro.policy.PolicyGuardrails;
import com.premaseem.maestro.policy.SecretsGuardrail;
import com.premaseem.maestro.retry.RetryPolicy;
import com.premaseem.maestro.retry.Sleeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Wires the AgentStrategy (Strategy pattern) and the orchestrator. The LLM
 * strategy is entirely opt-in: with no ANTHROPIC_API_KEY set, Maestro runs
 * fully offline on the deterministic scaffold generator rather than failing
 * to start — the agent token is optional by design, not a missing feature.
 */
@Configuration
public class OrchestratorConfig {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorConfig.class);

    @Bean
    public AgentStrategy agentStrategy() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            log.info("ANTHROPIC_API_KEY is set — using LlmAgentStrategy");
            return new LlmAgentStrategy(AnthropicLlmClient.fromEnvironment());
        }
        log.info("ANTHROPIC_API_KEY is not set — falling back to DeterministicAgentStrategy");
        return new DeterministicAgentStrategy();
    }

    @Bean(destroyMethod = "close")
    public WorkflowOrchestrator workflowOrchestrator(AgentStrategy agentStrategy) {
        PolicyGuardrails guardrails = new PolicyGuardrails(List.of(new SecretsGuardrail(), new DiffSizeGuardrail(20_000)));
        RetryPolicy retryPolicy = RetryPolicy.of(3, Duration.ofMillis(500));
        return new WorkflowOrchestrator(agentStrategy, guardrails, retryPolicy, Sleeper.threadSleeping(),
                Clock.systemUTC(), Executors.newVirtualThreadPerTaskExecutor());
    }
}
