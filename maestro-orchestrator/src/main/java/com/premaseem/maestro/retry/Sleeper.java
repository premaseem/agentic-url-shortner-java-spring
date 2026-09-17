package com.premaseem.maestro.retry;

import java.time.Duration;

/**
 * Seam for the delay between retry attempts, so tests never actually block.
 */
public interface Sleeper {

    void sleep(Duration duration);

    static Sleeper threadSleeping() {
        return duration -> {
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to retry", e);
            }
        };
    }

    static Sleeper noOp() {
        return duration -> {
        };
    }
}
