package com.premaseem.maestro.service;

public class RunNotFoundException extends RuntimeException {

    public RunNotFoundException(String runId) {
        super("No run found with id: " + runId);
    }
}
