package com.premaseem.maestro.policy;

import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.engine.StageResult;

import java.util.regex.Pattern;

/**
 * Security guardrail: blocks a stage from proceeding if its output looks
 * like it embeds a credential. Can only inspect output types that opt in
 * via {@link Reviewable} — anything else passes by default, since the
 * guardrail has no text to scan.
 */
public final class SecretsGuardrail implements PolicyGuardrail {

    private static final Pattern SUSPICIOUS_ASSIGNMENT =
            Pattern.compile("(?i)(api[_-]?key|secret|password|token)\\s*[:=]\\s*[\"']?\\S{6,}");
    private static final Pattern AWS_ACCESS_KEY = Pattern.compile("AKIA[0-9A-Z]{16}");

    @Override
    public GuardrailResult evaluate(Stage stage, StageResult result) {
        if (!(result.output() instanceof Reviewable reviewable)) {
            return GuardrailResult.pass();
        }

        String text = reviewable.reviewableText();
        if (SUSPICIOUS_ASSIGNMENT.matcher(text).find() || AWS_ACCESS_KEY.matcher(text).find()) {
            return GuardrailResult.fail("Possible secret/credential detected in " + stage + " output");
        }
        return GuardrailResult.pass();
    }
}
