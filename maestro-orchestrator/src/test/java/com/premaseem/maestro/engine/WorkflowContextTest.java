package com.premaseem.maestro.engine;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.premaseem.maestro.engine.Stage.DESIGN;
import static com.premaseem.maestro.engine.Stage.REQUIREMENT_ANALYSIS;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowContextTest {

    private final WorkflowContext context = new WorkflowContext();

    @Test
    void resultOfUnrecordedStageIsEmpty() {
        assertThat(context.resultOf(DESIGN)).isEmpty();
    }

    @Test
    void recordedResultIsRetrievableByStage() {
        StageResult result = new StageResult("analysis output", "summary", false, null, 1,
                Instant.parse("2026-09-17T10:00:00Z"), Instant.parse("2026-09-17T10:00:01Z"));

        context.recordResult(REQUIREMENT_ANALYSIS, result);

        assertThat(context.resultOf(REQUIREMENT_ANALYSIS)).contains(result);
    }

    @Test
    void clearingAResultRemovesIt() {
        StageResult result = new StageResult("out", "summary", false, null, 1,
                Instant.now(), Instant.now());
        context.recordResult(DESIGN, result);

        context.clearResult(DESIGN);

        assertThat(context.resultOf(DESIGN)).isEmpty();
    }

    @Test
    void decisionsAreRecordedInOrderAsAnImmutableLineage() {
        Instant first = Instant.parse("2026-09-17T10:00:00Z");
        Instant second = Instant.parse("2026-09-17T10:00:05Z");

        context.recordDecision(REQUIREMENT_ANALYSIS, first, "Interpreted requirement");
        context.recordDecision(DESIGN, second, "Proposed design");

        assertThat(context.lineage()).containsExactly(
                new DecisionRecord(REQUIREMENT_ANALYSIS, first, "Interpreted requirement"),
                new DecisionRecord(DESIGN, second, "Proposed design"));
    }
}
