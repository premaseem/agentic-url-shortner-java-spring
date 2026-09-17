package com.premaseem.maestro.engine;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cross-stage state for one workflow run: each stage's result (so
 * downstream stages can read what upstream stages decided) plus an
 * append-only decision lineage for audit-grade traceability. Thread-safe,
 * since stages with no dependency between them (e.g. TESTING and
 * DOCUMENTATION) execute concurrently against the same context.
 */
public final class WorkflowContext {

    private final Map<Stage, StageResult> results = new ConcurrentHashMap<>();
    private final List<DecisionRecord> lineage = new CopyOnWriteArrayList<>();

    public void recordResult(Stage stage, StageResult result) {
        results.put(stage, result);
    }

    public Optional<StageResult> resultOf(Stage stage) {
        return Optional.ofNullable(results.get(stage));
    }

    public Map<Stage, StageResult> allResults() {
        return Map.copyOf(results);
    }

    public void clearResult(Stage stage) {
        results.remove(stage);
    }

    public void recordDecision(Stage stage, Instant timestamp, String summary) {
        lineage.add(new DecisionRecord(stage, timestamp, summary));
    }

    public List<DecisionRecord> lineage() {
        return List.copyOf(lineage);
    }
}
