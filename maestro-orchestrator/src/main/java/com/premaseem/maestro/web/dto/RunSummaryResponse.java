package com.premaseem.maestro.web.dto;

import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.engine.StageStatus;
import com.premaseem.maestro.orchestrator.WorkflowRun;

import java.time.Instant;
import java.util.Map;

public record RunSummaryResponse(
        String id,
        String requirement,
        Map<Stage, StageStatus> statuses,
        boolean failed,
        boolean terminal,
        Instant startedAt,
        Instant completedAt) {

    public static RunSummaryResponse from(WorkflowRun run) {
        return new RunSummaryResponse(run.id(), run.originalRequirement(), run.statuses(),
                run.isFailed(), run.isTerminal(), run.startedAt(), run.completedAt());
    }
}
