package com.premaseem.maestro.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectRequest(@NotBlank(message = "reason must not be blank") String reason) {
}
