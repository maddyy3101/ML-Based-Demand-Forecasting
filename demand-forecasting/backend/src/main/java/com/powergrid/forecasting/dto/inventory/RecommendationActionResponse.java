package com.powergrid.forecasting.dto.inventory;

import java.time.Instant;

public record RecommendationActionResponse(
        String actionId,
        String inventoryId,
        String actionType,
        int recommendedOrderQty,
        String note,
        String actedBy,
        Instant createdAt
) {
}
