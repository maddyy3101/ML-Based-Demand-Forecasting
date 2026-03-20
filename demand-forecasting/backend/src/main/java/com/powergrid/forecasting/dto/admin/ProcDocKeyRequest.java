package com.powergrid.forecasting.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record ProcDocKeyRequest(
        @NotBlank(message = "API key is required.")
        String apiKey
) {
}

