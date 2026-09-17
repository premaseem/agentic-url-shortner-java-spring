package com.premaseem.maestro.engine;

import java.time.Instant;

/**
 * A single entry in a run's decision lineage — audit-grade traceability of
 * what happened at each stage and why, in the order it happened.
 */
public record DecisionRecord(Stage stage, Instant timestamp, String summary) {
}
