package com.demandforecast.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class ReplenishmentPlanResponse {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant generatedAt;
    int itemCount;
    Summary summary;
    List<ItemPlan> items;

    @Value
    @Builder
    public static class Summary {
        double totalRecommendedOrderQty;
        int highRiskItems;
        int mediumRiskItems;
        int lowRiskItems;
    }

    @Value
    @Builder
    public static class ItemPlan {
        String productId;
        String warehouseId;
        double meanDailyDemand;
        double demandStdDev;
        double demandCv;
        double safetyStock;
        double reorderPoint;
        double targetStockLevel;
        double inventoryPosition;
        double projectedCoverDays;
        double recommendedOrderQuantity;
        RiskLevel riskLevel;
        double stockoutRiskScore;
        double overstockRiskScore;
    }
}
