package com.demandforecast.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class InventoryExceptionResponse {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant generatedAt;
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate fromDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate toDate;
    int exceptionCount;
    List<ExceptionItem> exceptions;

    @Value
    @Builder
    public static class ExceptionItem {
        UUID predictionId;
        String productId;
        String warehouseId;
        String category;
        String region;
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate forecastDate;
        StockExceptionType type;
        RiskLevel riskLevel;
        double predictedDemand;
        double inventoryPosition;
        double coverageDays;
        double riskScore;
        String recommendation;
    }
}
