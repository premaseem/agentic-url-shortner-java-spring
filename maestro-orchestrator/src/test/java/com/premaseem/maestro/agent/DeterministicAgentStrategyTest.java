package com.premaseem.maestro.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicAgentStrategyTest {

    private final DeterministicAgentStrategy strategy = new DeterministicAgentStrategy();

    @Test
    void vagueRequirementIsFlaggedAmbiguousWithClarifyingQuestions() {
        RequirementAnalysis analysis = strategy.interpretRequirement("make it better");

        assertThat(analysis.ambiguous()).isTrue();
        assertThat(analysis.clarifyingQuestions()).isNotEmpty();
    }

    @Test
    void concreteRequirementIsNotAmbiguous() {
        RequirementAnalysis analysis = strategy.interpretRequirement(
                "Add an endpoint that generates a QR code image for an existing short URL");

        assertThat(analysis.ambiguous()).isFalse();
        assertThat(analysis.clarifyingQuestions()).isEmpty();
    }

    @Test
    void brownfieldRequirementIdentifiesImpactedFilesFromTheShortenerCodebase() {
        RequirementAnalysis analysis = strategy.interpretRequirement(
                "Add handling so expired short links return a clear error instead of redirecting");

        DesignProposal design = strategy.design(analysis);

        assertThat(design.impactedFiles()).isNotEmpty();
        assertThat(design.impactedFiles()).anyMatch(f -> f.contains("ShortUrl"));
        assertThat(design.tasks()).isNotEmpty();
    }

    @Test
    void greenfieldRequirementHasNoImpactedFiles() {
        RequirementAnalysis analysis = strategy.interpretRequirement(
                "Add an endpoint that generates a QR code image for an existing short URL");

        DesignProposal design = strategy.design(analysis);

        assertThat(design.impactedFiles()).isEmpty();
    }

    @Test
    void fullPipelineProducesNonEmptyArtifactsAndDisclosesItIsAScaffold() {
        RequirementAnalysis analysis = strategy.interpretRequirement(
                "Add an endpoint that generates a QR code image for an existing short URL");
        DesignProposal design = strategy.design(analysis);
        CodePatch patch = strategy.implement(design);
        TestSuite tests = strategy.writeTests(patch);
        Documentation docs = strategy.writeDocs(patch);
        ReleaseReadinessReport report = strategy.assessReleaseReadiness(patch, tests, docs);

        assertThat(patch.diff()).isNotBlank();
        assertThat(patch.touchedFiles()).isNotEmpty();
        assertThat(tests.testNames()).isNotEmpty();
        assertThat(docs.content()).isNotBlank();
        assertThat(report.ready()).isTrue();
        assertThat(report.risks()).anyMatch(risk -> risk.toLowerCase().contains("deterministic"));
    }
}
