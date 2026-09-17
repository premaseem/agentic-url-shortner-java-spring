package com.premaseem.maestro.orchestrator;

import com.premaseem.maestro.agent.AgentStrategy;
import com.premaseem.maestro.agent.CodePatch;
import com.premaseem.maestro.agent.DesignProposal;
import com.premaseem.maestro.agent.DeterministicAgentStrategy;
import com.premaseem.maestro.agent.Documentation;
import com.premaseem.maestro.agent.RequirementAnalysis;
import com.premaseem.maestro.agent.ReleaseReadinessReport;
import com.premaseem.maestro.agent.Task;
import com.premaseem.maestro.agent.TestSuite;
import com.premaseem.maestro.audit.MetricsCalculator;
import com.premaseem.maestro.audit.RunMetrics;
import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.engine.StageStatus;
import com.premaseem.maestro.policy.GuardrailResult;
import com.premaseem.maestro.policy.PolicyGuardrail;
import com.premaseem.maestro.policy.PolicyGuardrails;
import com.premaseem.maestro.retry.RetryPolicy;
import com.premaseem.maestro.retry.Sleeper;
import com.premaseem.maestro.retry.TransientStageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowOrchestratorTest {

    private static final RequirementAnalysis CONCRETE_ANALYSIS =
            new RequirementAnalysis("Add QR code endpoint", false, List.of(), List.of("scoped to shortener"));
    private static final RequirementAnalysis AMBIGUOUS_ANALYSIS =
            new RequirementAnalysis("make it better", true, List.of("what exactly?"), List.of());
    private static final DesignProposal DESIGN =
            new DesignProposal("approach", List.of(new Task("t1", "do it")), List.of());
    private static final CodePatch PATCH =
            new CodePatch("url-shortener-service", "diff content", List.of("File.java"));
    private static final TestSuite TESTS = new TestSuite(List.of("test1"), "summary");
    private static final Documentation DOCS = new Documentation("docs content");
    private static final ReleaseReadinessReport REPORT = new ReleaseReadinessReport(true, List.of("risk1"), "summary");

    @Mock
    private AgentStrategy strategy;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-17T10:00:00Z"), ZoneOffset.UTC);
    private ExecutorService executor;
    private WorkflowOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Single-threaded: Mockito mocks aren't safe under genuine concurrent
        // invocation, and TESTING/DOCUMENTATION are scheduled in the same
        // tick. Real concurrency against the (thread-safe) engine is proven
        // separately below with DeterministicAgentStrategy instead of a mock.
        executor = Executors.newSingleThreadExecutor();
        orchestrator = new WorkflowOrchestrator(strategy, new PolicyGuardrails(List.of()),
                RetryPolicy.of(3, Duration.ofMillis(1)), Sleeper.noOp(), clock, executor);
    }

    @AfterEach
    void tearDown() {
        orchestrator.close();
    }

    private void stubThroughImplementation() {
        when(strategy.interpretRequirement(anyString())).thenReturn(CONCRETE_ANALYSIS);
        when(strategy.design(CONCRETE_ANALYSIS)).thenReturn(DESIGN);
        when(strategy.implement(DESIGN)).thenReturn(PATCH);
    }

    private void stubHappyPath() {
        stubThroughImplementation();
        when(strategy.writeTests(PATCH)).thenReturn(TESTS);
        when(strategy.writeDocs(PATCH)).thenReturn(DOCS);
        when(strategy.assessReleaseReadiness(PATCH, TESTS, DOCS)).thenReturn(REPORT);
    }

    @Test
    void fullGreenfieldRunCompletesAllStagesWithApprovalsGranted() {
        stubHappyPath();

        WorkflowRun run = orchestrator.start("Add QR code endpoint");

        assertThat(run.statusOf(Stage.REQUIREMENT_ANALYSIS)).isEqualTo(StageStatus.COMPLETED);
        assertThat(run.statusOf(Stage.DESIGN)).isEqualTo(StageStatus.COMPLETED);
        assertThat(run.statusOf(Stage.IMPLEMENTATION)).isEqualTo(StageStatus.AWAITING_APPROVAL);
        assertThat(run.statusOf(Stage.TESTING)).isEqualTo(StageStatus.PENDING);
        assertThat(run.statusOf(Stage.DOCUMENTATION)).isEqualTo(StageStatus.PENDING);
        assertThat(run.statusOf(Stage.RELEASE_READINESS)).isEqualTo(StageStatus.PENDING);

        orchestrator.approve(run, Stage.IMPLEMENTATION);

        assertThat(run.statusOf(Stage.TESTING)).isEqualTo(StageStatus.COMPLETED);
        assertThat(run.statusOf(Stage.DOCUMENTATION)).isEqualTo(StageStatus.COMPLETED);
        assertThat(run.statusOf(Stage.RELEASE_READINESS)).isEqualTo(StageStatus.AWAITING_APPROVAL);

        orchestrator.approve(run, Stage.RELEASE_READINESS);

        assertThat(run.statusOf(Stage.RELEASE_READINESS)).isEqualTo(StageStatus.COMPLETED);
        assertThat(run.isTerminal()).isTrue();
        assertThat(run.isFailed()).isFalse();

        RunMetrics metrics = MetricsCalculator.calculate(run);
        assertThat(metrics.successRate()).isEqualTo(1.0);
        assertThat(metrics.rollbackCount()).isZero();
    }

    @Test
    void ambiguousRequirementGatesAtRequirementAnalysisBeforeDesignRuns() {
        when(strategy.interpretRequirement(anyString())).thenReturn(AMBIGUOUS_ANALYSIS);

        WorkflowRun run = orchestrator.start("make it better");

        assertThat(run.statusOf(Stage.REQUIREMENT_ANALYSIS)).isEqualTo(StageStatus.AWAITING_APPROVAL);
        assertThat(run.statusOf(Stage.DESIGN)).isEqualTo(StageStatus.PENDING);
        verify(strategy, never()).design(any());
    }

    @Test
    void rejectionAtImplementationRollsBackAndSkipsDownstreamAndSafeStops() {
        stubThroughImplementation();
        WorkflowRun run = orchestrator.start("Add QR code endpoint");

        orchestrator.reject(run, Stage.IMPLEMENTATION, "not what we want");

        assertThat(run.statusOf(Stage.IMPLEMENTATION)).isEqualTo(StageStatus.ROLLED_BACK);
        assertThat(run.statusOf(Stage.TESTING)).isEqualTo(StageStatus.SKIPPED);
        assertThat(run.statusOf(Stage.DOCUMENTATION)).isEqualTo(StageStatus.SKIPPED);
        assertThat(run.statusOf(Stage.RELEASE_READINESS)).isEqualTo(StageStatus.SKIPPED);
        assertThat(run.isFailed()).isTrue();
        assertThat(run.isTerminal()).isTrue();
        assertThat(run.rollbackCount()).isEqualTo(1);
    }

    @Test
    void retryRecoversFromATransientFailureDuringImplementation() {
        when(strategy.interpretRequirement(anyString())).thenReturn(CONCRETE_ANALYSIS);
        when(strategy.design(CONCRETE_ANALYSIS)).thenReturn(DESIGN);
        when(strategy.implement(DESIGN))
                .thenThrow(new TransientStageException("flaky 1"))
                .thenThrow(new TransientStageException("flaky 2"))
                .thenReturn(PATCH);

        WorkflowRun run = orchestrator.start("Add QR code endpoint");

        assertThat(run.statusOf(Stage.IMPLEMENTATION)).isEqualTo(StageStatus.AWAITING_APPROVAL);
        assertThat(run.retryAttempts()).isEqualTo(2);
        verify(strategy, times(3)).implement(DESIGN);
    }

    @Test
    void retryExhaustionFailsTheStageAndSafeStops() {
        when(strategy.interpretRequirement(anyString())).thenReturn(CONCRETE_ANALYSIS);
        when(strategy.design(CONCRETE_ANALYSIS)).thenReturn(DESIGN);
        when(strategy.implement(DESIGN)).thenThrow(new TransientStageException("always fails"));

        WorkflowRun run = orchestrator.start("Add QR code endpoint");

        assertThat(run.statusOf(Stage.IMPLEMENTATION)).isEqualTo(StageStatus.FAILED);
        assertThat(run.statusOf(Stage.TESTING)).isEqualTo(StageStatus.SKIPPED);
        assertThat(run.isFailed()).isTrue();
        assertThat(run.retryAttempts()).isEqualTo(2);
        verify(strategy, times(3)).implement(DESIGN);
    }

    @Test
    void policyGuardrailFailureFailsTheStageImmediatelyWithoutRetrying() {
        PolicyGuardrail alwaysBlocksImplementation = (stage, result) -> stage == Stage.IMPLEMENTATION
                ? GuardrailResult.fail("blocked by test guardrail")
                : GuardrailResult.pass();

        try (WorkflowOrchestrator guarded = new WorkflowOrchestrator(strategy,
                new PolicyGuardrails(List.of(alwaysBlocksImplementation)),
                RetryPolicy.of(3, Duration.ofMillis(1)), Sleeper.noOp(), clock, Executors.newFixedThreadPool(2))) {

            when(strategy.interpretRequirement(anyString())).thenReturn(CONCRETE_ANALYSIS);
            when(strategy.design(CONCRETE_ANALYSIS)).thenReturn(DESIGN);
            when(strategy.implement(DESIGN)).thenReturn(PATCH);

            WorkflowRun run = guarded.start("Add QR code endpoint");

            assertThat(run.statusOf(Stage.IMPLEMENTATION)).isEqualTo(StageStatus.FAILED);
            assertThat(run.isFailed()).isTrue();
            verify(strategy, times(1)).implement(DESIGN);
        }
    }

    @Test
    void revisingAStageReexecutesOnlyItsDownstreamSubgraph() {
        stubHappyPath();
        WorkflowRun run = orchestrator.start("Add QR code endpoint");
        orchestrator.approve(run, Stage.IMPLEMENTATION);
        orchestrator.approve(run, Stage.RELEASE_READINESS);
        assertThat(run.isTerminal()).isTrue();

        orchestrator.reviseStage(run, Stage.DESIGN, "reconsider approach");

        assertThat(run.statusOf(Stage.REQUIREMENT_ANALYSIS)).isEqualTo(StageStatus.COMPLETED);
        assertThat(run.statusOf(Stage.DESIGN)).isEqualTo(StageStatus.COMPLETED);
        assertThat(run.statusOf(Stage.IMPLEMENTATION)).isEqualTo(StageStatus.AWAITING_APPROVAL);
        verify(strategy, times(1)).interpretRequirement(anyString());
        verify(strategy, times(2)).design(CONCRETE_ANALYSIS);
        verify(strategy, times(2)).implement(DESIGN);

        orchestrator.approve(run, Stage.IMPLEMENTATION);
        orchestrator.approve(run, Stage.RELEASE_READINESS);

        assertThat(run.isTerminal()).isTrue();
        assertThat(run.isFailed()).isFalse();
    }

    /**
     * The scenarios above use a single-threaded executor to keep the shared
     * Mockito mock race-free. This test instead uses a real, thread-safe
     * {@link DeterministicAgentStrategy} against a genuinely multi-threaded
     * executor, proving the engine itself (WorkflowContext, WorkflowRun) is
     * safe when TESTING and DOCUMENTATION really do execute concurrently.
     */
    @Test
    void engineIsSafeUnderRealConcurrentExecutionOfParallelStages() {
        try (WorkflowOrchestrator concurrent = new WorkflowOrchestrator(new DeterministicAgentStrategy(),
                new PolicyGuardrails(List.of()), RetryPolicy.of(3, Duration.ofMillis(1)), Sleeper.noOp(),
                clock, Executors.newFixedThreadPool(4))) {

            WorkflowRun run = concurrent.start("Add an endpoint that generates a QR code for a short URL");
            concurrent.approve(run, Stage.IMPLEMENTATION);

            assertThat(run.statusOf(Stage.TESTING)).isEqualTo(StageStatus.COMPLETED);
            assertThat(run.statusOf(Stage.DOCUMENTATION)).isEqualTo(StageStatus.COMPLETED);
            assertThat(run.statusOf(Stage.RELEASE_READINESS)).isEqualTo(StageStatus.AWAITING_APPROVAL);

            concurrent.approve(run, Stage.RELEASE_READINESS);

            assertThat(run.isTerminal()).isTrue();
            assertThat(run.isFailed()).isFalse();
        }
    }
}
