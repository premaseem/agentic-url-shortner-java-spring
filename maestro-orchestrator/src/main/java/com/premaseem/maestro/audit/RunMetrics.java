package com.premaseem.maestro.audit;

import java.time.Duration;

/**
 * Reliability metrics for one workflow run, as required by the assignment:
 * success rate, retry/rollback frequency, mean time to recovery, and
 * end-to-end latency.
 */
public record RunMetrics(
        int totalStages,
        int completedStages,
        double successRate,
        int retryCount,
        int rollbackCount,
        Duration meanTimeToRecovery,
        Duration totalLatency) {
}
