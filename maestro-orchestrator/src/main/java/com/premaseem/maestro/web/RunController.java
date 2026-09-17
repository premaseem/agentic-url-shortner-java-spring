package com.premaseem.maestro.web;

import com.premaseem.maestro.audit.MetricsCalculator;
import com.premaseem.maestro.audit.RunMetrics;
import com.premaseem.maestro.engine.DecisionRecord;
import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.engine.StageResult;
import com.premaseem.maestro.orchestrator.WorkflowRun;
import com.premaseem.maestro.service.RunService;
import com.premaseem.maestro.web.dto.RejectRequest;
import com.premaseem.maestro.web.dto.ReviseRequest;
import com.premaseem.maestro.web.dto.RunSummaryResponse;
import com.premaseem.maestro.web.dto.StartRunRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final RunService runService;

    public RunController(RunService runService) {
        this.runService = runService;
    }

    @PostMapping
    public ResponseEntity<RunSummaryResponse> start(@Valid @RequestBody StartRunRequest request) {
        WorkflowRun run = runService.startRun(request.requirement());
        return ResponseEntity.status(HttpStatus.CREATED).body(RunSummaryResponse.from(run));
    }

    @GetMapping
    public List<RunSummaryResponse> list() {
        return runService.listRuns().stream().map(RunSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public RunSummaryResponse get(@PathVariable String id) {
        return RunSummaryResponse.from(runService.getRun(id));
    }

    @PostMapping("/{id}/stages/{stage}/approve")
    public RunSummaryResponse approve(@PathVariable String id, @PathVariable Stage stage) {
        return RunSummaryResponse.from(runService.approve(id, stage));
    }

    @PostMapping("/{id}/stages/{stage}/reject")
    public RunSummaryResponse reject(@PathVariable String id, @PathVariable Stage stage,
            @Valid @RequestBody RejectRequest request) {
        return RunSummaryResponse.from(runService.reject(id, stage, request.reason()));
    }

    @PostMapping("/{id}/stages/{stage}/revise")
    public RunSummaryResponse revise(@PathVariable String id, @PathVariable Stage stage,
            @Valid @RequestBody ReviseRequest request) {
        return RunSummaryResponse.from(runService.revise(id, stage, request.note()));
    }

    @GetMapping("/{id}/artifacts")
    public Map<Stage, StageResult> artifacts(@PathVariable String id) {
        return runService.getRun(id).context().allResults();
    }

    @GetMapping("/{id}/lineage")
    public List<DecisionRecord> lineage(@PathVariable String id) {
        return runService.getRun(id).context().lineage();
    }

    @GetMapping("/{id}/metrics")
    public RunMetrics metrics(@PathVariable String id) {
        return MetricsCalculator.calculate(runService.getRun(id));
    }
}
