package com.premaseem.maestro.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.premaseem.maestro.engine.Stage;
import com.premaseem.maestro.orchestrator.InvalidStageTransitionException;
import com.premaseem.maestro.orchestrator.WorkflowOrchestrator;
import com.premaseem.maestro.orchestrator.WorkflowRun;
import com.premaseem.maestro.policy.PolicyGuardrails;
import com.premaseem.maestro.retry.RetryPolicy;
import com.premaseem.maestro.retry.Sleeper;
import com.premaseem.maestro.service.RunNotFoundException;
import com.premaseem.maestro.service.RunService;
import com.premaseem.maestro.web.dto.RejectRequest;
import com.premaseem.maestro.web.dto.ReviseRequest;
import com.premaseem.maestro.web.dto.StartRunRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RunController.class)
class RunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RunService runService;

    private static WorkflowRun realRun(String requirement) {
        // WorkflowRun's constructor/mutators are package-private to
        // orchestrator on purpose (see its javadoc) -- drive a real,
        // throwaway orchestrator+DeterministicAgentStrategy instead of
        // trying to hand-construct one, so this test only relies on the
        // public WorkflowRun API just like production code would.
        try (WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                new com.premaseem.maestro.agent.DeterministicAgentStrategy(), new PolicyGuardrails(List.of()),
                RetryPolicy.of(1, Duration.ofMillis(1)), Sleeper.noOp(),
                Clock.fixed(Instant.parse("2026-09-17T10:00:00Z"), ZoneOffset.UTC),
                Executors.newFixedThreadPool(2))) {
            return orchestrator.start(requirement);
        }
    }

    @Test
    void startRunReturns201WithSummary() throws Exception {
        WorkflowRun run = realRun("Add an endpoint that generates a QR code for a short URL");
        when(runService.startRun("Add QR code endpoint")).thenReturn(run);

        mockMvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartRunRequest("Add QR code endpoint"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(run.id()))
                .andExpect(jsonPath("$.statuses.IMPLEMENTATION").value("AWAITING_APPROVAL"));
    }

    @Test
    void startRunWithBlankRequirementReturns400() throws Exception {
        mockMvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartRunRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRunReturns200WhenFound() throws Exception {
        WorkflowRun run = realRun("Add an endpoint that generates a QR code for a short URL");
        when(runService.getRun(run.id())).thenReturn(run);

        mockMvc.perform(get("/api/runs/" + run.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(run.id()));
    }

    @Test
    void getRunReturns404WhenNotFound() throws Exception {
        when(runService.getRun("missing")).thenThrow(new RunNotFoundException("missing"));

        mockMvc.perform(get("/api/runs/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approveReturns200WithUpdatedSummary() throws Exception {
        WorkflowRun run = realRun("Add an endpoint that generates a QR code for a short URL");
        when(runService.approve(eq(run.id()), eq(Stage.IMPLEMENTATION))).thenReturn(run);

        mockMvc.perform(post("/api/runs/" + run.id() + "/stages/IMPLEMENTATION/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(run.id()));
    }

    @Test
    void approveOnAStageNotAwaitingApprovalReturns409() throws Exception {
        when(runService.approve(eq("run-1"), eq(Stage.TESTING)))
                .thenThrow(new InvalidStageTransitionException(Stage.TESTING, "PENDING"));

        mockMvc.perform(post("/api/runs/run-1/stages/TESTING/approve"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectReturns200AndPassesTheReasonThrough() throws Exception {
        WorkflowRun run = realRun("Add an endpoint that generates a QR code for a short URL");
        when(runService.reject(eq(run.id()), eq(Stage.IMPLEMENTATION), eq("not what we want"))).thenReturn(run);

        mockMvc.perform(post("/api/runs/" + run.id() + "/stages/IMPLEMENTATION/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RejectRequest("not what we want"))))
                .andExpect(status().isOk());
    }

    @Test
    void reviseReturns200AndPassesTheNoteThrough() throws Exception {
        WorkflowRun run = realRun("Add an endpoint that generates a QR code for a short URL");
        when(runService.revise(eq(run.id()), eq(Stage.DESIGN), eq("reconsider"))).thenReturn(run);

        mockMvc.perform(post("/api/runs/" + run.id() + "/stages/DESIGN/revise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviseRequest("reconsider"))))
                .andExpect(status().isOk());
    }

    @Test
    void artifactsReturnsStageResultsFromTheRunContext() throws Exception {
        WorkflowRun run = realRun("Add an endpoint that generates a QR code for a short URL");
        when(runService.getRun(run.id())).thenReturn(run);

        mockMvc.perform(get("/api/runs/" + run.id() + "/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.REQUIREMENT_ANALYSIS.attempts").value(1));
    }

    @Test
    void lineageReturnsTheDecisionRecordsFromTheRunContext() throws Exception {
        WorkflowRun run = realRun("Add an endpoint that generates a QR code for a short URL");
        when(runService.getRun(run.id())).thenReturn(run);

        mockMvc.perform(get("/api/runs/" + run.id() + "/lineage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void metricsReturnsComputedRunMetrics() throws Exception {
        WorkflowRun run = realRun("Add an endpoint that generates a QR code for a short URL");
        when(runService.getRun(run.id())).thenReturn(run);

        mockMvc.perform(get("/api/runs/" + run.id() + "/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStages").value(6));
    }
}
