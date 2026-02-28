package com.powergrid.forecasting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BacktestRequest(
        @NotBlank String from,
        @NotBlank String to,
        String materialType,
        String region,
        @Min(10) @Max(5000) int sampleSize
) {
}
