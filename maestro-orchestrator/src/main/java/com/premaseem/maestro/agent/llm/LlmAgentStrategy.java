package com.premaseem.maestro.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.premaseem.maestro.agent.AgentStrategy;
import com.premaseem.maestro.agent.CodePatch;
import com.premaseem.maestro.agent.DesignProposal;
import com.premaseem.maestro.agent.Documentation;
import com.premaseem.maestro.agent.ReleaseReadinessReport;
import com.premaseem.maestro.agent.RequirementAnalysis;
import com.premaseem.maestro.agent.TestSuite;
import com.premaseem.maestro.retry.TransientStageException;

/**
 * The real "thinking" strategy: each stage is a single call to an LLM,
 * prompted to answer with JSON matching that stage's output record exactly
 * (so Jackson can deserialize straight into it, no intermediate DTOs). A
 * malformed or non-JSON response is treated as transient — plugging
 * straight into {@link com.premaseem.maestro.retry.RetryExecutor} rather
 * than needing its own retry logic.
 */
public final class LlmAgentStrategy implements AgentStrategy {

    private final LlmClient client;
    private final ObjectMapper objectMapper;

    public LlmAgentStrategy(LlmClient client) {
        this(client, new ObjectMapper());
    }

    public LlmAgentStrategy(LlmClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public RequirementAnalysis interpretRequirement(String requirementText) {
        String prompt = """
                You are Maestro, an SDLC requirement analyst for a URL shortener service. \
                Read the requirement and respond with ONLY a JSON object (no markdown, no \
                commentary) matching exactly this shape:
                {"normalizedProblem": string, "ambiguous": boolean, "clarifyingQuestions": string[], "assumptions": string[]}
                Set ambiguous=true and list clarifyingQuestions if the requirement lacks enough \
                detail to design against safely.

                Requirement: %s
                """.formatted(requirementText);
        return parse(prompt, RequirementAnalysis.class);
    }

    @Override
    public DesignProposal design(RequirementAnalysis analysis) {
        String prompt = """
                Propose a design for this requirement against a Spring Boot URL shortener \
                (url-shortener-service). Respond with ONLY JSON matching:
                {"approachSummary": string, "tasks": [{"id": string, "description": string}], "impactedFiles": string[]}
                impactedFiles should list existing files this change touches, or be empty for a \
                net-new (greenfield) addition.

                Normalized requirement: %s
                Assumptions: %s
                """.formatted(analysis.normalizedProblem(), String.join("; ", analysis.assumptions()));
        return parse(prompt, DesignProposal.class);
    }

    @Override
    public CodePatch implement(DesignProposal design) {
        String prompt = """
                Implement this design as production-quality Java/Spring Boot code. Respond with \
                ONLY JSON matching:
                {"targetModule": string, "diff": string, "touchedFiles": string[]}
                diff should be a real unified-diff-style patch, not a placeholder.

                Approach: %s
                Impacted files: %s
                """.formatted(design.approachSummary(), String.join(", ", design.impactedFiles()));
        return parse(prompt, CodePatch.class);
    }

    @Override
    public TestSuite writeTests(CodePatch patch) {
        String prompt = """
                Write unit/integration tests (happy path and corner cases) for this patch. \
                Respond with ONLY JSON matching:
                {"testNames": string[], "summary": string}

                Target module: %s
                Diff: %s
                """.formatted(patch.targetModule(), patch.diff());
        return parse(prompt, TestSuite.class);
    }

    @Override
    public Documentation writeDocs(CodePatch patch) {
        String prompt = """
                Write concise documentation for this change. Respond with ONLY JSON matching:
                {"content": string}

                Target module: %s
                Diff: %s
                """.formatted(patch.targetModule(), patch.diff());
        return parse(prompt, Documentation.class);
    }

    @Override
    public ReleaseReadinessReport assessReleaseReadiness(CodePatch patch, TestSuite tests, Documentation docs) {
        String prompt = """
                Assess release readiness for this change. Identify risks, trade-offs, and failure \
                scenarios. Respond with ONLY JSON matching:
                {"ready": boolean, "risks": string[], "summary": string}

                Target module: %s
                Tests: %s
                Docs: %s
                """.formatted(patch.targetModule(), String.join(", ", tests.testNames()), docs.content());
        return parse(prompt, ReleaseReadinessReport.class);
    }

    private <T> T parse(String prompt, Class<T> type) {
        String response = client.complete(prompt);
        try {
            return objectMapper.readValue(stripCodeFences(response), type);
        } catch (Exception e) {
            throw new TransientStageException(
                    "Failed to parse LLM response as " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private String stripCodeFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\n?", "").replaceFirst("```\\s*$", "");
        }
        return trimmed.trim();
    }
}
