package com.premaseem.maestro.policy;

import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.engine.StageResult;

import java.util.List;

/**
 * Runs a set of {@link PolicyGuardrail}s against a stage result, short
 * circuiting on the first failure.
 */
public final class PolicyGuardrails {

    private final List<PolicyGuardrail> guardrails;

    public PolicyGuardrails(List<PolicyGuardrail> guardrails) {
        this.guardrails = List.copyOf(guardrails);
    }

    public GuardrailResult evaluate(Stage stage, StageResult result) {
        for (PolicyGuardrail guardrail : guardrails) {
            GuardrailResult outcome = guardrail.evaluate(stage, result);
            if (!outcome.passed()) {
                return outcome;
            }
        }
        return GuardrailResult.pass();
    }
}
