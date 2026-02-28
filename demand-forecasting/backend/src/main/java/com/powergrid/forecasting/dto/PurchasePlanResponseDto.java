package com.powergrid.forecasting.dto;

import java.util.List;

public record PurchasePlanResponseDto(
        String planMonth,
        List<Line> purchaseLines,
        double totalBudgetInr
) {
    public record Line(
            String materialType,
            String region,
            int recommendedQty,
            String unitLabel,
            double estimatedCostInr,
            String urgency
    ) {
    }
}
