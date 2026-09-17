package com.premaseem.maestro.audit;

import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.engine.StageResult;
import com.premaseem.maestro.engine.StageStatus;
import com.premaseem.maestro.orchestrator.WorkflowRun;

import java.time.Duration;
import java.util.Map;

/**
 * Pure function from a run's current state to {@link RunMetrics} — takes no
 * clock of its own, so total latency is only reported once the run has
 * actually reached a terminal state (completedAt is set); an in-progress
 * run reports {@link Duration#ZERO} rather than guessing at "now".
 */
public final class MetricsCalculator {

    private MetricsCalculator() {
    }

    public static RunMetrics calculate(WorkflowRun run) {
        Map<Stage, StageStatus> statuses = run.statuses();
        int totalStages = statuses.size();
        long completedStages = statuses.values().stream().filter(status -> status == StageStatus.COMPLETED).count();
        double successRate = totalStages == 0 ? 0.0 : (double) completedStages / totalStages;

        Duration totalLatency = run.completedAt() != null
                ? Duration.between(run.startedAt(), run.completedAt())
                : Duration.ZERO;

        Duration meanTimeToRecovery = meanTimeToRecovery(run.context().allResults());

        return new RunMetrics(totalStages, (int) completedStages, successRate,
                run.retryAttempts(), run.rollbackCount(), meanTimeToRecovery, totalLatency);
    }

    /**
     * Average execution time of stages that needed at least one retry
     * before eventually succeeding — how long the retry loop took to
     * recover, on average. Stages that never retried, or that failed
     * permanently (and so never produced a result), don't factor in.
     */
    private static Duration meanTimeToRecovery(Map<Stage, StageResult> results) {
        var recovered = results.values().stream().filter(result -> result.attempts() > 1).toList();
        if (recovered.isEmpty()) {
            return Duration.ZERO;
        }
        Duration total = Duration.ZERO;
        for (StageResult result : recovered) {
            total = total.plus(Duration.between(result.startedAt(), result.finishedAt()));
        }
        return total.dividedBy(recovered.size());
    }
}
