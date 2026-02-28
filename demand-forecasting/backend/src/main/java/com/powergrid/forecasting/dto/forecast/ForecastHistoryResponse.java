package com.powergrid.forecasting.dto.forecast;

import java.time.Instant;

public record ForecastHistoryResponse(
        String id,
        String requestId,
        String projectId,
        String projectPhase,
        String state,
        String region,
        String materialType,
        int predictedQuantity,
        Integer actualQuantity,
        String procurementDecision,
        String requestedBy,
        Instant createdAt
) {
}
