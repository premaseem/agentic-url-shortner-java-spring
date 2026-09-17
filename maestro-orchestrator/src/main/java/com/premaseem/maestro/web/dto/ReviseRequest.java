package com.premaseem.maestro.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviseRequest(@NotBlank(message = "note must not be blank") String note) {
}
