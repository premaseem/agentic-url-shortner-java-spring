package com.premaseem.maestro.retry;

import java.util.function.Supplier;

/**
 * Runs an action with bounded retries: only {@link TransientStageException}
 * is retried (up to {@code policy.maxAttempts()}); any other exception is
 * treated as a hard, non-retryable failure and propagates immediately.
 */
public final class RetryExecutor {

    private final RetryPolicy policy;
    private final Sleeper sleeper;

    public RetryExecutor(RetryPolicy policy, Sleeper sleeper) {
        this.policy = policy;
        this.sleeper = sleeper;
    }

    public <T> RetryOutcome<T> execute(Supplier<T> action) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return new RetryOutcome<>(action.get(), attempts, null);
            } catch (TransientStageException e) {
                if (attempts >= policy.maxAttempts()) {
                    return new RetryOutcome<>(null, attempts, e);
                }
                sleeper.sleep(policy.delayBetweenAttempts());
            }
        }
    }
}
