package com.premaseem.maestro.policy;

import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.engine.StageResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SecretsGuardrailTest {

    private final SecretsGuardrail guardrail = new SecretsGuardrail();

    @Test
    void passesCleanCode() {
        StageResult result = resultOf("public String greet() { return \"hello, premaseem\"; }");

        assertThat(guardrail.evaluate(Stage.IMPLEMENTATION, result).passed()).isTrue();
    }

    @Test
    void failsWhenAnApiKeyAssignmentIsPresent() {
        StageResult result = resultOf("String apiKey = \"sk-live-abcdef1234567890\";");

        GuardrailResult outcome = guardrail.evaluate(Stage.IMPLEMENTATION, result);

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).containsIgnoringCase("secret");
    }

    @Test
    void failsWhenAnAwsStyleAccessKeyIsPresent() {
        StageResult result = resultOf("credentials.set(\"AKIAABCDEFGHIJKLMNOP\");");

        assertThat(guardrail.evaluate(Stage.IMPLEMENTATION, result).passed()).isFalse();
    }

    @Test
    void passesWhenOutputIsNotReviewable() {
        StageResult result = new StageResult("plain string output, nothing to inspect", "n/a",
                false, null, 1, Instant.now(), Instant.now());

        assertThat(guardrail.evaluate(Stage.IMPLEMENTATION, result).passed()).isTrue();
    }

    private StageResult resultOf(String text) {
        Reviewable output = () -> text;
        return new StageResult(output, "test", false, null, 1, Instant.now(), Instant.now());
    }
}
