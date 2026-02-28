package com.powergrid.forecasting.dto.forecast;

import java.time.Instant;

public record ForecastResponse(
        String forecastId,
        String requestId,
        String projectId,
        String materialType,
        String unitLabel,
        int predictedQuantity,
        String procurementDecision,
        String decisionMessage,
        double modelConfidence,
        String modelType,
        Instant createdAt
) {
}
