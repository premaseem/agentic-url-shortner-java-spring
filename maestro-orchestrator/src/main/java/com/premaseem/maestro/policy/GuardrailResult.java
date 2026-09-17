package com.premaseem.maestro.policy;

public record GuardrailResult(boolean passed, String message) {

    public static GuardrailResult pass() {
        return new GuardrailResult(true, null);
    }

    public static GuardrailResult fail(String message) {
        return new GuardrailResult(false, message);
    }
}
