package com.powergrid.forecasting.dto.forecast;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ForecastRequest(
        @NotBlank String projectId,
        @NotBlank String projectPhase,
        @NotBlank String state,
        @NotBlank String region,
        @NotBlank String terrainType,
        @NotBlank String towerType,
        @NotBlank String substationType,
        @Min(50) @Max(298) int transmissionLengthKm,
        @Min(302) @Max(1997) int budgetCrore,
        @NotBlank String materialType,
        @Min(15) @Max(59) int leadTimeDays,
        @DecimalMin("12.0") @DecimalMax("22.0") double taxPercentage,
        @Positive double transportationCost,
        @Positive double historicalConsumption,
        @Min(1) @Max(12) int forecastMonth,
        @Min(2023) @Max(2030) int forecastYear
) {
}
