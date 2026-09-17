package com.premaseem.maestro.engine;

/**
 * Lifecycle of a single stage within one workflow run. STALE marks a stage
 * (and its downstream dependents) whose upstream output changed and that
 * therefore needs to re-execute — the mechanism behind dynamic re-planning.
 */
public enum StageStatus {
    PENDING,
    RUNNING,
    AWAITING_APPROVAL,
    COMPLETED,
    FAILED,
    ROLLED_BACK,
    SKIPPED,
    STALE
}
