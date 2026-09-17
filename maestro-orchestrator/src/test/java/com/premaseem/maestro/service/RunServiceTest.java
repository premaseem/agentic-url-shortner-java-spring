package com.premaseem.maestro.service;

import com.premaseem.maestro.agent.DeterministicAgentStrategy;
import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.engine.StageStatus;
import com.premaseem.maestro.orchestrator.WorkflowOrchestrator;
import com.premaseem.maestro.orchestrator.WorkflowRun;
import com.premaseem.maestro.policy.PolicyGuardrails;
import com.premaseem.maestro.retry.RetryPolicy;
import com.premaseem.maestro.retry.Sleeper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunServiceTest {

    // Real orchestrator + the deterministic (offline, no-token-required)
    // strategy -- exercises RunService's bookkeeping on top of genuinely
    // working orchestration, no mocks needed.
    private WorkflowOrchestrator orchestrator;
    private RunService runService;

    @BeforeEach
    void setUp() {
        orchestrator = new WorkflowOrchestrator(new DeterministicAgentStrategy(), new PolicyGuardrails(List.of()),
                RetryPolicy.of(2, Duration.ofMillis(1)), Sleeper.noOp(), Clock.systemUTC(),
                Executors.newFixedThreadPool(2));
        runService = new RunService(orchestrator);
    }

    @AfterEach
    void tearDown() {
        orchestrator.close();
    }

    @Test
    void startRunCreatesAndStoresARunRetrievableById() {
        WorkflowRun started = runService.startRun("Add an endpoint that generates a QR code for a short URL");

        WorkflowRun fetched = runService.getRun(started.id());

        assertThat(fetched.id()).isEqualTo(started.id());
        assertThat(fetched.statusOf(Stage.IMPLEMENTATION)).isEqualTo(StageStatus.AWAITING_APPROVAL);
    }

    @Test
    void getRunWithUnknownIdThrows() {
        assertThatThrownBy(() -> runService.getRun("does-not-exist"))
                .isInstanceOf(RunNotFoundException.class);
    }

    @Test
    void approveAndRejectDelegateToTheOrchestratorAndPersistTheMutation() {
        WorkflowRun run = runService.startRun("Add an endpoint that generates a QR code for a short URL");

        runService.approve(run.id(), Stage.IMPLEMENTATION);
        assertThat(runService.getRun(run.id()).statusOf(Stage.RELEASE_READINESS))
                .isEqualTo(StageStatus.AWAITING_APPROVAL);

        runService.reject(run.id(), Stage.RELEASE_READINESS, "not ready");
        WorkflowRun rejected = runService.getRun(run.id());
        assertThat(rejected.statusOf(Stage.RELEASE_READINESS)).isEqualTo(StageStatus.ROLLED_BACK);
        assertThat(rejected.isFailed()).isTrue();
    }

    @Test
    void reviseDelegatesToTheOrchestratorForTargetedReplan() {
        WorkflowRun run = runService.startRun("Add an endpoint that generates a QR code for a short URL");
        runService.approve(run.id(), Stage.IMPLEMENTATION);
        runService.approve(run.id(), Stage.RELEASE_READINESS);
        assertThat(runService.getRun(run.id()).isTerminal()).isTrue();

        runService.revise(run.id(), Stage.DESIGN, "reconsider the approach");

        WorkflowRun revised = runService.getRun(run.id());
        assertThat(revised.statusOf(Stage.REQUIREMENT_ANALYSIS)).isEqualTo(StageStatus.COMPLETED);
        assertThat(revised.statusOf(Stage.IMPLEMENTATION)).isEqualTo(StageStatus.AWAITING_APPROVAL);
    }

    @Test
    void listRunsReturnsEveryStartedRun() {
        WorkflowRun first = runService.startRun("Add an endpoint that generates a QR code for a short URL");
        WorkflowRun second = runService.startRun("Add handling so expired short links return a clear error");

        assertThat(runService.listRuns()).extracting(WorkflowRun::id)
                .containsExactlyInAnyOrder(first.id(), second.id());
    }
}
