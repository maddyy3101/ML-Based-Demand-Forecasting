package com.powergrid.forecasting.dto.inventory;

public record RecommendationActionRequest(
        Integer recommendedOrderQty,
        String note
) {
}
