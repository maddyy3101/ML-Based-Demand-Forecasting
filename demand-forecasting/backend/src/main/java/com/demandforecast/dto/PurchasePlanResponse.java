package com.demandforecast.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class PurchasePlanResponse {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant generatedAt;
    int horizonDays;
    int itemCount;
    Summary summary;
    List<ItemPlan> items;

    @Value
    @Builder
    public static class Summary {
        double totalPlannedOrderQty;
        double totalProjectedStockoutUnits;
        int totalStockoutDays;
    }

    @Value
    @Builder
    public static class ItemPlan {
        String productId;
        String warehouseId;
        double totalForecastDemand;
        double totalPlannedOrderQty;
        double projectedEndingInventory;
        double projectedStockoutUnits;
        int stockoutDays;
        int overstockDays;
        RiskLevel riskLevel;
        double serviceLevelEstimate;
        List<OrderLine> orders;
    }

    @Value
    @Builder
    public static class OrderLine {
        int orderDay;
        int arrivalDay;
        double quantity;
        String reason;
    }
}
