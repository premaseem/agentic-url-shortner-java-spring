package com.premaseem.maestro.agent;

/**
 * The pluggable "thinking" layer behind each SDLC stage. The orchestration
 * engine (graph, gates, retries, rollback, metrics) never depends on a
 * concrete strategy — {@link DeterministicAgentStrategy} is a reproducible,
 * offline fallback; a real LLM-backed implementation can be swapped in
 * without touching engine code.
 */
public interface AgentStrategy {

    RequirementAnalysis interpretRequirement(String requirementText);

    DesignProposal design(RequirementAnalysis analysis);

    CodePatch implement(DesignProposal design);

    TestSuite writeTests(CodePatch patch);

    // Only depends on the code patch, not on TestSuite: DOCUMENTATION and
    // TESTING are parallel siblings in the graph (both depend on
    // IMPLEMENTATION only) and execute concurrently, so this must not read
    // TESTING's output.
    Documentation writeDocs(CodePatch patch);

    ReleaseReadinessReport assessReleaseReadiness(CodePatch patch, TestSuite tests, Documentation docs);
}
