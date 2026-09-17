package com.premaseem.maestro.retry;

/**
 * Marks a stage failure as transient and therefore worth retrying (e.g. a
 * flaky downstream call). Any other exception is treated as a hard failure
 * — such as a policy guardrail rejection — and is never retried, since
 * retrying wouldn't change the outcome.
 */
public class TransientStageException extends RuntimeException {

    public TransientStageException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransientStageException(String message) {
        super(message);
    }
}
