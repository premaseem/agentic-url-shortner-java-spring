package com.premaseem.maestro.agent.llm;

import com.premaseem.maestro.agent.CodePatch;
import com.premaseem.maestro.agent.DesignProposal;
import com.premaseem.maestro.agent.Documentation;
import com.premaseem.maestro.agent.ReleaseReadinessReport;
import com.premaseem.maestro.agent.RequirementAnalysis;
import com.premaseem.maestro.agent.TestSuite;
import com.premaseem.maestro.retry.TransientStageException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmAgentStrategyTest {

    @Test
    void interpretRequirementParsesValidJsonFromTheModel() {
        LlmAgentStrategy strategy = new LlmAgentStrategy(prompt -> """
                {"normalizedProblem":"Add a QR code endpoint","ambiguous":false,
                 "clarifyingQuestions":[],"assumptions":["scoped to shortener"]}
                """);

        RequirementAnalysis analysis = strategy.interpretRequirement("add a QR code endpoint");

        assertThat(analysis.normalizedProblem()).isEqualTo("Add a QR code endpoint");
        assertThat(analysis.ambiguous()).isFalse();
        assertThat(analysis.assumptions()).containsExactly("scoped to shortener");
    }

    @Test
    void interpretRequirementStripsMarkdownCodeFencesBeforeParsing() {
        LlmAgentStrategy strategy = new LlmAgentStrategy(prompt -> """
                ```json
                {"normalizedProblem":"Add a QR code endpoint","ambiguous":false,
                 "clarifyingQuestions":[],"assumptions":[]}
                ```
                """);

        RequirementAnalysis analysis = strategy.interpretRequirement("add a QR code endpoint");

        assertThat(analysis.normalizedProblem()).isEqualTo("Add a QR code endpoint");
    }

    @Test
    void malformedJsonIsReportedAsATransientFailureSoItCanBeRetried() {
        LlmAgentStrategy strategy = new LlmAgentStrategy(prompt -> "not json at all");

        assertThatThrownBy(() -> strategy.interpretRequirement("anything"))
                .isInstanceOf(TransientStageException.class);
    }

    @Test
    void designParsesNestedTasksAndImpactedFiles() {
        LlmAgentStrategy strategy = new LlmAgentStrategy(prompt -> """
                {"approachSummary":"Add QR support","tasks":[{"id":"t1","description":"Add endpoint"}],
                 "impactedFiles":["ShortUrlController.java"]}
                """);
        RequirementAnalysis analysis = new RequirementAnalysis("Add a QR code endpoint", false, java.util.List.of(),
                java.util.List.of());

        DesignProposal design = strategy.design(analysis);

        assertThat(design.tasks()).hasSize(1);
        assertThat(design.tasks().get(0).description()).isEqualTo("Add endpoint");
        assertThat(design.impactedFiles()).containsExactly("ShortUrlController.java");
    }

    @Test
    void implementWriteTestsWriteDocsAndAssessReleaseReadinessAllParseTheirShapes() {
        DesignProposal design = new DesignProposal("summary", java.util.List.of(), java.util.List.of());

        LlmAgentStrategy implementStrategy = new LlmAgentStrategy(prompt -> """
                {"targetModule":"url-shortener-service","diff":"real diff","touchedFiles":["A.java"]}
                """);
        CodePatch patch = implementStrategy.implement(design);
        assertThat(patch.diff()).isEqualTo("real diff");

        LlmAgentStrategy testsStrategy = new LlmAgentStrategy(prompt -> """
                {"testNames":["happyPathTest"],"summary":"covers happy path"}
                """);
        TestSuite tests = testsStrategy.writeTests(patch);
        assertThat(tests.testNames()).containsExactly("happyPathTest");

        LlmAgentStrategy docsStrategy = new LlmAgentStrategy(prompt -> """
                {"content":"## Change\\nDocs here"}
                """);
        Documentation docs = docsStrategy.writeDocs(patch);
        assertThat(docs.content()).contains("Docs here");

        LlmAgentStrategy releaseStrategy = new LlmAgentStrategy(prompt -> """
                {"ready":true,"risks":["none major"],"summary":"good to go"}
                """);
        ReleaseReadinessReport report = releaseStrategy.assessReleaseReadiness(patch, tests, docs);
        assertThat(report.ready()).isTrue();
        assertThat(report.risks()).containsExactly("none major");
    }
}
