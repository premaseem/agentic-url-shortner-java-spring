package com.premaseem.maestro.policy;

import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.engine.StageResult;

/**
 * Change-control guardrail: caps how large a single stage's output may be,
 * as a crude but effective proxy for "this change is too big to review
 * safely in one gate."
 */
public final class DiffSizeGuardrail implements PolicyGuardrail {

    private final int maxCharacters;

    public DiffSizeGuardrail(int maxCharacters) {
        this.maxCharacters = maxCharacters;
    }

    @Override
    public GuardrailResult evaluate(Stage stage, StageResult result) {
        if (!(result.output() instanceof Reviewable reviewable)) {
            return GuardrailResult.pass();
        }

        int length = reviewable.reviewableText().length();
        if (length > maxCharacters) {
            return GuardrailResult.fail(
                    stage + " output is too large to review safely (" + length + " > " + maxCharacters + " chars)");
        }
        return GuardrailResult.pass();
    }
}
