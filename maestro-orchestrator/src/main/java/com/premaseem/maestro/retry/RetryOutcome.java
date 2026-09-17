package com.premaseem.maestro.retry;

public record RetryOutcome<T>(T value, int attempts, TransientStageException failure) {

    public boolean succeeded() {
        return failure == null;
    }
}
