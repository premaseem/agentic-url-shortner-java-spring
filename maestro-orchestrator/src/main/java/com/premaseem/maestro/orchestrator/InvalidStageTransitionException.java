package com.premaseem.maestro.orchestrator;

import com.premaseem.maestro.engine.Stage;

/**
 * Thrown when approve/reject is called on a stage that isn't currently
 * AWAITING_APPROVAL — a client-state error, distinct from an internal
 * engine bug, so the REST layer can map it to a specific HTTP status
 * without catching unrelated {@code IllegalStateException}s too broadly.
 */
public class InvalidStageTransitionException extends RuntimeException {

    public InvalidStageTransitionException(Stage stage, String currentStatus) {
        super(stage + " is not awaiting approval (status=" + currentStatus + ")");
    }
}
