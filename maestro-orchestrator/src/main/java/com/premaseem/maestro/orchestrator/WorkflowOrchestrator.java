package com.premaseem.maestro.orchestrator;

import com.premaseem.maestro.agent.AgentStrategy;
import com.premaseem.maestro.agent.CodePatch;
import com.premaseem.maestro.agent.DesignProposal;
import com.premaseem.maestro.agent.Documentation;
import com.premaseem.maestro.agent.RequirementAnalysis;
import com.premaseem.maestro.agent.TestSuite;
import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.engine.StageResult;
import com.premaseem.maestro.engine.StageStatus;
import com.premaseem.maestro.engine.WorkflowGraph;
import com.premaseem.maestro.policy.GuardrailResult;
import com.premaseem.maestro.policy.PolicyGuardrails;
import com.premaseem.maestro.policy.Reviewable;
import com.premaseem.maestro.retry.RetryExecutor;
import com.premaseem.maestro.retry.RetryOutcome;
import com.premaseem.maestro.retry.RetryPolicy;
import com.premaseem.maestro.retry.Sleeper;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Coordinates one workflow run across the standard SDLC graph: computes the
 * ready set each tick and executes it (sequentially where a stage has a
 * single predecessor, concurrently where siblings share one — e.g. TESTING
 * and DOCUMENTATION — synchronizing before the next tick discovers newly
 * ready stages), applies bounded retries, evaluates policy guardrails as an
 * exit gate, escalates statically- or dynamically-gated stages to human
 * approval, and safe-stops with rollback of downstream work on any hard
 * failure or rejection.
 */
public final class WorkflowOrchestrator implements AutoCloseable {

    private final AgentStrategy strategy;
    private final PolicyGuardrails policyGuardrails;
    private final RetryPolicy retryPolicy;
    private final Sleeper sleeper;
    private final Clock clock;
    private final ExecutorService executor;

    public WorkflowOrchestrator(AgentStrategy strategy, PolicyGuardrails policyGuardrails, RetryPolicy retryPolicy,
            Sleeper sleeper, Clock clock, ExecutorService executor) {
        this.strategy = strategy;
        this.policyGuardrails = policyGuardrails;
        this.retryPolicy = retryPolicy;
        this.sleeper = sleeper;
        this.clock = clock;
        this.executor = executor;
    }

    public WorkflowRun start(String requirementText) {
        WorkflowRun run = new WorkflowRun(UUID.randomUUID().toString(), requirementText,
                WorkflowGraph.standardSdlcGraph(), clock.instant());
        advance(run);
        return run;
    }

    public void approve(WorkflowRun run, Stage stage) {
        requireAwaitingApproval(run, stage);
        run.setStatus(stage, StageStatus.COMPLETED);
        run.context().recordDecision(stage, clock.instant(), "Approved by human reviewer");
        advance(run);
    }

    public void reject(WorkflowRun run, Stage stage, String reason) {
        requireAwaitingApproval(run, stage);
        haltRun(run, stage, StageStatus.ROLLED_BACK, "Rejected by human reviewer: " + reason);
    }

    /**
     * Dynamic re-plan: marks {@code stage} and everything that transitively
     * depends on it as STALE (discarding their prior results) and resumes
     * execution — only the affected subgraph re-executes; anything outside
     * it (e.g. an unrelated upstream stage) is untouched.
     */
    public void reviseStage(WorkflowRun run, Stage stage, String note) {
        Set<Stage> affected = new LinkedHashSet<>();
        affected.add(stage);
        affected.addAll(run.graph().transitiveDependents(stage));

        for (Stage affectedStage : affected) {
            run.context().clearResult(affectedStage);
            run.setStatus(affectedStage, StageStatus.STALE);
        }
        if (run.isTerminal()) {
            run.reopen();
        }
        run.context().recordDecision(stage, clock.instant(),
                "Revised: " + note + " -- marking downstream stale for re-plan");
        advance(run);
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    private void advance(WorkflowRun run) {
        if (run.isFailed()) {
            return;
        }
        while (true) {
            Set<Stage> ready = run.graph().readyStages(run.liveStatuses());
            if (ready.isEmpty()) {
                if (allCompleted(run)) {
                    run.complete(clock.instant());
                }
                return;
            }

            CompletableFuture<?>[] futures = ready.stream()
                    .peek(stage -> run.setStatus(stage, StageStatus.RUNNING))
                    .map(stage -> CompletableFuture.runAsync(() -> runStage(run, stage), executor))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).join();

            if (run.isFailed()) {
                return;
            }
        }
    }

    private boolean allCompleted(WorkflowRun run) {
        return run.statuses().values().stream().allMatch(status -> status == StageStatus.COMPLETED);
    }

    private void runStage(WorkflowRun run, Stage stage) {
        Instant startedAt = clock.instant();
        run.context().recordDecision(stage, startedAt, "Starting " + stage);

        RetryExecutor retryExecutor = new RetryExecutor(retryPolicy, sleeper);
        RetryOutcome<Object> outcome;
        try {
            outcome = retryExecutor.execute(() -> dispatch(run, stage));
        } catch (RuntimeException hardFailure) {
            haltRun(run, stage, StageStatus.FAILED, "Hard failure: " + hardFailure.getMessage());
            return;
        }

        run.addRetryAttempts(outcome.attempts() - 1);

        if (!outcome.succeeded()) {
            haltRun(run, stage, StageStatus.FAILED, "Exhausted retries: " + outcome.failure().getMessage());
            return;
        }

        Object output = outcome.value();
        Instant finishedAt = clock.instant();
        StageResult provisional = new StageResult(
                output, summaryFor(output), false, null, outcome.attempts(), startedAt, finishedAt);

        GuardrailResult guardrailResult = policyGuardrails.evaluate(stage, provisional);
        if (!guardrailResult.passed()) {
            haltRun(run, stage, StageStatus.FAILED, "Policy guardrail failed: " + guardrailResult.message());
            return;
        }

        boolean dynamicReview = requiresDynamicReview(output);
        StageResult finalResult = dynamicReview
                ? new StageResult(output, provisional.summary(), true,
                        "Ambiguous requirement needs clarification", outcome.attempts(), startedAt, finishedAt)
                : provisional;
        run.context().recordResult(stage, finalResult);

        if (run.graph().alwaysRequiresApproval(stage) || dynamicReview) {
            run.setStatus(stage, StageStatus.AWAITING_APPROVAL);
            run.context().recordDecision(stage, finishedAt, stage + " awaiting human approval");
        } else {
            run.setStatus(stage, StageStatus.COMPLETED);
            run.context().recordDecision(stage, finishedAt,
                    "Completed " + stage + " (" + outcome.attempts() + " attempt(s))");
        }
    }

    private void haltRun(WorkflowRun run, Stage stage, StageStatus stageStatus, String reason) {
        run.setStatus(stage, stageStatus);
        run.context().recordDecision(stage, clock.instant(), reason);

        for (Stage dependent : run.graph().transitiveDependents(stage)) {
            StageStatus dependentStatus = run.statusOf(dependent);
            if (dependentStatus == StageStatus.PENDING || dependentStatus == StageStatus.STALE) {
                run.setStatus(dependent, StageStatus.SKIPPED);
            }
        }

        run.incrementRollbackCount();
        run.markFailed();
        run.complete(clock.instant());
        run.context().recordDecision(stage, clock.instant(), "Run safe-stopped");
    }

    private void requireAwaitingApproval(WorkflowRun run, Stage stage) {
        if (run.statusOf(stage) != StageStatus.AWAITING_APPROVAL) {
            throw new InvalidStageTransitionException(stage, String.valueOf(run.statusOf(stage)));
        }
    }

    private Object dispatch(WorkflowRun run, Stage stage) {
        return switch (stage) {
            case REQUIREMENT_ANALYSIS -> strategy.interpretRequirement(run.originalRequirement());
            case DESIGN -> strategy.design(requiredOutput(run, Stage.REQUIREMENT_ANALYSIS, RequirementAnalysis.class));
            case IMPLEMENTATION -> strategy.implement(requiredOutput(run, Stage.DESIGN, DesignProposal.class));
            case TESTING -> strategy.writeTests(requiredOutput(run, Stage.IMPLEMENTATION, CodePatch.class));
            case DOCUMENTATION -> strategy.writeDocs(requiredOutput(run, Stage.IMPLEMENTATION, CodePatch.class));
            case RELEASE_READINESS -> strategy.assessReleaseReadiness(
                    requiredOutput(run, Stage.IMPLEMENTATION, CodePatch.class),
                    requiredOutput(run, Stage.TESTING, TestSuite.class),
                    requiredOutput(run, Stage.DOCUMENTATION, Documentation.class));
        };
    }

    private <T> T requiredOutput(WorkflowRun run, Stage stage, Class<T> type) {
        return run.context().resultOf(stage)
                .map(StageResult::output)
                .map(type::cast)
                .orElseThrow(() -> new IllegalStateException("Missing required output from " + stage));
    }

    private boolean requiresDynamicReview(Object output) {
        return output instanceof RequirementAnalysis analysis && analysis.ambiguous();
    }

    private String summaryFor(Object output) {
        if (output instanceof Reviewable reviewable) {
            String text = reviewable.reviewableText();
            return text.length() > 200 ? text.substring(0, 200) + "..." : text;
        }
        return String.valueOf(output);
    }
}
