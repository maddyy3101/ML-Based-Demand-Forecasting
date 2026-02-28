package com.powergrid.forecasting.dto;

import java.util.List;

public record ReplenishmentResponseDto(
        List<Line> lines,
        String generatedAt
) {
    public record Line(
            String inventoryId,
            String materialType,
            double demandVariability,
            int safetyStock,
            int reorderPoint,
            int recommendedOrderQty
    ) {
    }
}
