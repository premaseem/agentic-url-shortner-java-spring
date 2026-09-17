package com.premaseem.maestro.policy;

/**
 * Implemented by any agent-output type that wants policy guardrails to be
 * able to inspect its content, without guardrails needing to know the
 * concrete output type of every stage.
 */
public interface Reviewable {

    String reviewableText();
}
