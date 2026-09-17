package com.premaseem.maestro.orchestrator;

import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.engine.StageStatus;
import com.premaseem.maestro.engine.WorkflowContext;
import com.premaseem.maestro.engine.WorkflowGraph;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mutable state for a single workflow run. Status mutation is package-private
 * — only {@link WorkflowOrchestrator} is allowed to change a run's state;
 * everyone else (tests, a future REST layer) reads it.
 */
public final class WorkflowRun {

    private final String id;
    private final String originalRequirement;
    private final WorkflowGraph graph;
    private final WorkflowContext context;
    private final Map<Stage, StageStatus> statuses = new ConcurrentHashMap<>();
    private final Instant startedAt;
    private final AtomicInteger retryAttempts = new AtomicInteger();
    private final AtomicInteger rollbackCount = new AtomicInteger();
    private volatile Instant completedAt;
    private volatile boolean failed;

    WorkflowRun(String id, String originalRequirement, WorkflowGraph graph, Instant startedAt) {
        this.id = id;
        this.originalRequirement = originalRequirement;
        this.graph = graph;
        this.context = new WorkflowContext();
        this.startedAt = startedAt;
        for (Stage stage : graph.allStages()) {
            statuses.put(stage, StageStatus.PENDING);
        }
    }

    public String id() {
        return id;
    }

    public String originalRequirement() {
        return originalRequirement;
    }

    public WorkflowGraph graph() {
        return graph;
    }

    public WorkflowContext context() {
        return context;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public boolean isFailed() {
        return failed;
    }

    public boolean isTerminal() {
        return completedAt != null;
    }

    public int retryAttempts() {
        return retryAttempts.get();
    }

    public int rollbackCount() {
        return rollbackCount.get();
    }

    public Map<Stage, StageStatus> statuses() {
        return Map.copyOf(statuses);
    }

    public StageStatus statusOf(Stage stage) {
        return statuses.get(stage);
    }

    Map<Stage, StageStatus> liveStatuses() {
        return statuses;
    }

    void setStatus(Stage stage, StageStatus status) {
        statuses.put(stage, status);
    }

    void addRetryAttempts(int attempts) {
        if (attempts > 0) {
            retryAttempts.addAndGet(attempts);
        }
    }

    void incrementRollbackCount() {
        rollbackCount.incrementAndGet();
    }

    void markFailed() {
        failed = true;
    }

    void reopen() {
        failed = false;
        completedAt = null;
    }

    void complete(Instant at) {
        completedAt = at;
    }
}
