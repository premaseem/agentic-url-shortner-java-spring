package com.premaseem.maestro.agent;

import com.premaseem.maestro.policy.Reviewable;

import java.util.List;

public record CodePatch(String targetModule, String diff, List<String> touchedFiles) implements Reviewable {

    @Override
    public String reviewableText() {
        return diff;
    }
}
