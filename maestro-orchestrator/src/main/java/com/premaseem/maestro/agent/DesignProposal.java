package com.premaseem.maestro.agent;

import com.premaseem.maestro.policy.Reviewable;

import java.util.List;

/**
 * {@code impactedFiles} is how brownfield codebase reasoning surfaces:
 * non-empty means the agent identified existing files this change touches;
 * empty means it read the requirement as greenfield (net-new).
 */
public record DesignProposal(
        String approachSummary,
        List<Task> tasks,
        List<String> impactedFiles) implements Reviewable {

    @Override
    public String reviewableText() {
        String taskList = tasks.stream().map(Task::description).reduce((a, b) -> a + "; " + b).orElse("");
        return approachSummary + " | tasks: " + taskList + " | impacted: " + String.join(", ", impactedFiles);
    }
}
