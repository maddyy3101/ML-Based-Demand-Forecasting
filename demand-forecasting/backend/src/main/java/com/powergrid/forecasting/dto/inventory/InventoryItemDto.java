package com.powergrid.forecasting.dto.inventory;

import java.time.Instant;

public record InventoryItemDto(
        String id,
        String materialType,
        String materialName,
        String unitLabel,
        String sku,
        String region,
        String towerType,
        int currentStock,
        int reorderThreshold,
        int maxCapacity,
        String stockStatus,
        double unitCostInr,
        String warehouseLocation,
        Instant lastUpdated
) {
}
