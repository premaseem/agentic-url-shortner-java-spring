package com.premaseem.maestro.retry;

import java.time.Duration;

public record RetryPolicy(int maxAttempts, Duration delayBetweenAttempts) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
    }

    public static RetryPolicy of(int maxAttempts, Duration delayBetweenAttempts) {
        return new RetryPolicy(maxAttempts, delayBetweenAttempts);
    }
}
