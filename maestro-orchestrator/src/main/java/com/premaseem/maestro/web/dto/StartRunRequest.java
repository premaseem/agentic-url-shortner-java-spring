package com.premaseem.maestro.web.dto;

import jakarta.validation.constraints.NotBlank;

public record StartRunRequest(@NotBlank(message = "requirement must not be blank") String requirement) {
}
