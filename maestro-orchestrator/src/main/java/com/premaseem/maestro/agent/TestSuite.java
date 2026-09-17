package com.premaseem.maestro.agent;

import com.premaseem.maestro.policy.Reviewable;

import java.util.List;

public record TestSuite(List<String> testNames, String summary) implements Reviewable {

    @Override
    public String reviewableText() {
        return summary + " | tests: " + String.join(", ", testNames);
    }
}
