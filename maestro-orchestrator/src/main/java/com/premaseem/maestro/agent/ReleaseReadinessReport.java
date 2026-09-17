package com.premaseem.maestro.agent;

import com.premaseem.maestro.policy.Reviewable;

import java.util.List;

public record ReleaseReadinessReport(boolean ready, List<String> risks, String summary) implements Reviewable {

    @Override
    public String reviewableText() {
        return summary + " | risks: " + String.join("; ", risks);
    }
}
