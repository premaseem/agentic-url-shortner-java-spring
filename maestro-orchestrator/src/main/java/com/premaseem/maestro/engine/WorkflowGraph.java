package com.premaseem.maestro.engine;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The dependency graph a {@code WorkflowGraph} instance describes an SDLC
 * pipeline as a DAG: each stage's entry gate is "all of its dependencies are
 * COMPLETED", which naturally yields sequential execution where a stage has
 * one predecessor and synchronized parallel execution where several
 * dependents (e.g. TESTING and DOCUMENTATION) share a predecessor and a
 * downstream join point (RELEASE_READINESS) waits on all of them.
 */
public final class WorkflowGraph {

    private final Map<Stage, Set<Stage>> dependencies;
    private final Set<Stage> alwaysGatedStages;

    private WorkflowGraph(Map<Stage, Set<Stage>> dependencies, Set<Stage> alwaysGatedStages) {
        this.dependencies = dependencies;
        this.alwaysGatedStages = alwaysGatedStages;
    }

    public static WorkflowGraph standardSdlcGraph() {
        Map<Stage, Set<Stage>> deps = new EnumMap<>(Stage.class);
        deps.put(Stage.REQUIREMENT_ANALYSIS, EnumSet.noneOf(Stage.class));
        deps.put(Stage.DESIGN, EnumSet.of(Stage.REQUIREMENT_ANALYSIS));
        deps.put(Stage.IMPLEMENTATION, EnumSet.of(Stage.DESIGN));
        deps.put(Stage.TESTING, EnumSet.of(Stage.IMPLEMENTATION));
        deps.put(Stage.DOCUMENTATION, EnumSet.of(Stage.IMPLEMENTATION));
        deps.put(Stage.RELEASE_READINESS, EnumSet.of(Stage.TESTING, Stage.DOCUMENTATION));

        Set<Stage> alwaysGated = EnumSet.of(Stage.IMPLEMENTATION, Stage.RELEASE_READINESS);

        return new WorkflowGraph(deps, alwaysGated);
    }

    public Set<Stage> dependenciesOf(Stage stage) {
        return EnumSet.copyOf(dependencies.get(stage));
    }

    public Set<Stage> allStages() {
        return EnumSet.copyOf(dependencies.keySet());
    }

    public boolean alwaysRequiresApproval(Stage stage) {
        return alwaysGatedStages.contains(stage);
    }

    /**
     * Stages whose dependencies are all COMPLETED and which are themselves
     * still awaiting execution (PENDING, or STALE after a re-plan).
     */
    public Set<Stage> readyStages(Map<Stage, StageStatus> statuses) {
        Set<Stage> ready = EnumSet.noneOf(Stage.class);
        for (Stage stage : dependencies.keySet()) {
            StageStatus status = statuses.get(stage);
            if (status != StageStatus.PENDING && status != StageStatus.STALE) {
                continue;
            }
            if (dependenciesSatisfied(stage, statuses)) {
                ready.add(stage);
            }
        }
        return ready;
    }

    private boolean dependenciesSatisfied(Stage stage, Map<Stage, StageStatus> statuses) {
        for (Stage dependency : dependencies.get(stage)) {
            if (statuses.get(dependency) != StageStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every stage reachable by following dependency edges forward from
     * {@code stage} (i.e. everything that transitively depends on it). Used
     * both to skip downstream work on rollback and to mark the affected
     * subgraph STALE on re-plan.
     */
    public Set<Stage> transitiveDependents(Stage stage) {
        Set<Stage> dependents = EnumSet.noneOf(Stage.class);
        collectDependents(stage, dependents);
        return dependents;
    }

    private void collectDependents(Stage stage, Set<Stage> accumulator) {
        for (Map.Entry<Stage, Set<Stage>> entry : dependencies.entrySet()) {
            Stage candidate = entry.getKey();
            if (entry.getValue().contains(stage) && accumulator.add(candidate)) {
                collectDependents(candidate, accumulator);
            }
        }
    }
}
