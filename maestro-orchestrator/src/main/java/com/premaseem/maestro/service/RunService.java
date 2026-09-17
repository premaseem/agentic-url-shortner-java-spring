package com.premaseem.maestro.service;

import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.orchestrator.WorkflowOrchestrator;
import com.premaseem.maestro.orchestrator.WorkflowRun;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin facade over {@link WorkflowOrchestrator} that adds an in-memory run
 * store, so the REST layer has something to fetch a run back by id. Runs
 * are not persisted across restarts -- a deliberate simplification for
 * this slice, not an oversight.
 */
@Service
public class RunService {

    private final WorkflowOrchestrator orchestrator;
    private final Map<String, WorkflowRun> runs = new ConcurrentHashMap<>();

    public RunService(WorkflowOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public WorkflowRun startRun(String requirement) {
        WorkflowRun run = orchestrator.start(requirement);
        runs.put(run.id(), run);
        return run;
    }

    public WorkflowRun getRun(String id) {
        WorkflowRun run = runs.get(id);
        if (run == null) {
            throw new RunNotFoundException(id);
        }
        return run;
    }

    public WorkflowRun approve(String id, Stage stage) {
        WorkflowRun run = getRun(id);
        orchestrator.approve(run, stage);
        return run;
    }

    public WorkflowRun reject(String id, Stage stage, String reason) {
        WorkflowRun run = getRun(id);
        orchestrator.reject(run, stage, reason);
        return run;
    }

    public WorkflowRun revise(String id, Stage stage, String note) {
        WorkflowRun run = getRun(id);
        orchestrator.reviseStage(run, stage, note);
        return run;
    }

    public List<WorkflowRun> listRuns() {
        return List.copyOf(runs.values());
    }
}
