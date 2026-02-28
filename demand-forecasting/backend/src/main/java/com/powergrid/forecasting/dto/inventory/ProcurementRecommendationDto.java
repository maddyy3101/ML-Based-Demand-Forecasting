package com.powergrid.forecasting.dto.inventory;

public record ProcurementRecommendationDto(
        String inventoryId,
        String materialType,
        String materialName,
        String unitLabel,
        String region,
        String towerType,
        int currentStock,
        int reorderThreshold,
        String stockStatus,
        int predictedDemand,
        double avgDailyDeployment,
        double daysUntilStockout,
        int recommendedOrderQty,
        String urgencyLevel,
        String urgencyReason
) {
}
