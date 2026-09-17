package com.premaseem.maestro.agent;

import com.premaseem.maestro.policy.Reviewable;

public record Documentation(String content) implements Reviewable {

    @Override
    public String reviewableText() {
        return content;
    }
}
