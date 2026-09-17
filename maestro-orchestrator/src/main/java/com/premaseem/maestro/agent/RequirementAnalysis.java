package com.premaseem.maestro.agent;

import com.premaseem.maestro.policy.Reviewable;

import java.util.List;

public record RequirementAnalysis(
        String normalizedProblem,
        boolean ambiguous,
        List<String> clarifyingQuestions,
        List<String> assumptions) implements Reviewable {

    @Override
    public String reviewableText() {
        return normalizedProblem + " | assumptions: " + String.join("; ", assumptions);
    }
}
