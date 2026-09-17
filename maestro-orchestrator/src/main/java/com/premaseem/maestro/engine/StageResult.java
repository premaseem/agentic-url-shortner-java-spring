package com.premaseem.maestro.engine;

import java.time.Instant;

/**
 * The outcome of one successful stage execution. {@code output} carries the
 * stage-specific artifact (a {@code RequirementAnalysis}, {@code CodePatch},
 * etc. from the agent package) as an opaque object — the engine itself
 * never needs to know its shape, only downstream stages and callers do.
 * {@code requiresHumanReview} lets a stage escalate to an approval gate
 * dynamically (e.g. an ambiguous requirement), independent of whether the
 * stage is statically gated in the graph.
 */
public record StageResult(
        Object output,
        String summary,
        boolean requiresHumanReview,
        String reviewReason,
        int attempts,
        Instant startedAt,
        Instant finishedAt) {
}
