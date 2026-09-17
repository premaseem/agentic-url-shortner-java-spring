package com.premaseem.maestro.policy;

import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.engine.StageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyGuardrailsTest {

    @Mock
    private PolicyGuardrail first;

    @Mock
    private PolicyGuardrail second;

    private final StageResult result = new StageResult("out", "summary", false, null, 1,
            Instant.now(), Instant.now());

    @Test
    void passesWhenEveryGuardrailPasses() {
        when(first.evaluate(Stage.IMPLEMENTATION, result)).thenReturn(GuardrailResult.pass());
        when(second.evaluate(Stage.IMPLEMENTATION, result)).thenReturn(GuardrailResult.pass());

        PolicyGuardrails guardrails = new PolicyGuardrails(List.of(first, second));

        assertThat(guardrails.evaluate(Stage.IMPLEMENTATION, result).passed()).isTrue();
    }

    @Test
    void shortCircuitsOnTheFirstFailureAndSkipsLaterGuardrails() {
        when(first.evaluate(Stage.IMPLEMENTATION, result)).thenReturn(GuardrailResult.fail("nope"));

        PolicyGuardrails guardrails = new PolicyGuardrails(List.of(first, second));

        GuardrailResult outcome = guardrails.evaluate(Stage.IMPLEMENTATION, result);

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).isEqualTo("nope");
        verify(second, never()).evaluate(Stage.IMPLEMENTATION, result);
    }
}
