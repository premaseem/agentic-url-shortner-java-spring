package com.premaseem.maestro.policy;

import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.engine.StageResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DiffSizeGuardrailTest {

    private final DiffSizeGuardrail guardrail = new DiffSizeGuardrail(20);

    @Test
    void passesWhenWithinLimit() {
        StageResult result = resultOf("short diff");

        assertThat(guardrail.evaluate(Stage.IMPLEMENTATION, result).passed()).isTrue();
    }

    @Test
    void failsWhenOverLimit() {
        StageResult result = resultOf("this diff is way more than twenty characters long");

        GuardrailResult outcome = guardrail.evaluate(Stage.IMPLEMENTATION, result);

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).containsIgnoringCase("too large");
    }

    private StageResult resultOf(String text) {
        Reviewable output = () -> text;
        return new StageResult(output, "test", false, null, 1, Instant.now(), Instant.now());
    }
}
