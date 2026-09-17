package com.premaseem.maestro.policy;

import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.engine.StageResult;

/**
 * A single exit-gate policy check run against a stage's result before it's
 * allowed to proceed (to approval, or to COMPLETED).
 */
public interface PolicyGuardrail {

    GuardrailResult evaluate(Stage stage, StageResult result);
}
