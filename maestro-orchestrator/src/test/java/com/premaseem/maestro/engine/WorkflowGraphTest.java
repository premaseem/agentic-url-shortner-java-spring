package com.premaseem.maestro.engine;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static com.premaseem.maestro.engine.Stage.DESIGN;
import static com.premaseem.maestro.engine.Stage.DOCUMENTATION;
import static com.premaseem.maestro.engine.Stage.IMPLEMENTATION;
import static com.premaseem.maestro.engine.Stage.RELEASE_READINESS;
import static com.premaseem.maestro.engine.Stage.REQUIREMENT_ANALYSIS;
import static com.premaseem.maestro.engine.Stage.TESTING;
import static com.premaseem.maestro.engine.StageStatus.COMPLETED;
import static com.premaseem.maestro.engine.StageStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowGraphTest {

    private final WorkflowGraph graph = WorkflowGraph.standardSdlcGraph();

    @Test
    void dependenciesReflectTheSdlcSequenceWithAJoinBeforeReleaseReadiness() {
        assertThat(graph.dependenciesOf(REQUIREMENT_ANALYSIS)).isEmpty();
        assertThat(graph.dependenciesOf(DESIGN)).containsExactly(REQUIREMENT_ANALYSIS);
        assertThat(graph.dependenciesOf(IMPLEMENTATION)).containsExactly(DESIGN);
        assertThat(graph.dependenciesOf(TESTING)).containsExactly(IMPLEMENTATION);
        assertThat(graph.dependenciesOf(DOCUMENTATION)).containsExactly(IMPLEMENTATION);
        assertThat(graph.dependenciesOf(RELEASE_READINESS)).containsExactlyInAnyOrder(TESTING, DOCUMENTATION);
    }

    @Test
    void onlyRequirementAnalysisIsReadyAtTheStart() {
        Map<Stage, StageStatus> statuses = allPending();

        assertThat(graph.readyStages(statuses)).containsExactly(REQUIREMENT_ANALYSIS);
    }

    @Test
    void testingAndDocumentationBecomeReadyTogetherAfterImplementation() {
        Map<Stage, StageStatus> statuses = allPending();
        statuses.put(REQUIREMENT_ANALYSIS, COMPLETED);
        statuses.put(DESIGN, COMPLETED);
        statuses.put(IMPLEMENTATION, COMPLETED);

        assertThat(graph.readyStages(statuses)).containsExactlyInAnyOrder(TESTING, DOCUMENTATION);
    }

    @Test
    void releaseReadinessWaitsForBothParallelSiblingsToSynchronize() {
        Map<Stage, StageStatus> statuses = allPending();
        statuses.put(REQUIREMENT_ANALYSIS, COMPLETED);
        statuses.put(DESIGN, COMPLETED);
        statuses.put(IMPLEMENTATION, COMPLETED);
        statuses.put(TESTING, COMPLETED);
        // DOCUMENTATION still pending

        assertThat(graph.readyStages(statuses)).doesNotContain(RELEASE_READINESS);

        statuses.put(DOCUMENTATION, COMPLETED);

        assertThat(graph.readyStages(statuses)).containsExactly(RELEASE_READINESS);
    }

    @Test
    void transitiveDependentsOfDesignCoverEverythingDownstream() {
        assertThat(graph.transitiveDependents(DESIGN))
                .containsExactlyInAnyOrder(IMPLEMENTATION, TESTING, DOCUMENTATION, RELEASE_READINESS);
    }

    @Test
    void transitiveDependentsOfTestingIsJustReleaseReadiness() {
        assertThat(graph.transitiveDependents(TESTING)).containsExactly(RELEASE_READINESS);
    }

    @Test
    void implementationAndReleaseReadinessAreAlwaysGatedForHumanApproval() {
        assertThat(graph.alwaysRequiresApproval(IMPLEMENTATION)).isTrue();
        assertThat(graph.alwaysRequiresApproval(RELEASE_READINESS)).isTrue();
        assertThat(graph.alwaysRequiresApproval(DESIGN)).isFalse();
    }

    private Map<Stage, StageStatus> allPending() {
        Map<Stage, StageStatus> statuses = new EnumMap<>(Stage.class);
        for (Stage stage : Stage.values()) {
            statuses.put(stage, PENDING);
        }
        return statuses;
    }
}
