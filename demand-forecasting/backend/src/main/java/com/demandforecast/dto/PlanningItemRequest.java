package com.demandforecast.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Value
@Builder
@Jacksonized
public class PlanningItemRequest {

    @NotBlank(message = "productId is required")
    String productId;

    @NotBlank(message = "warehouseId is required")
    String warehouseId;

    @DecimalMin(value = "0.0", message = "currentInventory must be >= 0")
    double currentInventory;

    @DecimalMin(value = "0.0", message = "onOrderInventory must be >= 0")
    @Builder.Default
    double onOrderInventory = 0.0;

    @Min(value = 1, message = "leadTimeDays must be >= 1")
    @Builder.Default
    int leadTimeDays = 1;

    @Min(value = 1, message = "reviewPeriodDays must be >= 1")
    @Builder.Default
    int reviewPeriodDays = 7;

    @DecimalMin(value = "0.50", message = "serviceLevel must be between 0.50 and 0.999")
    @DecimalMax(value = "0.999", message = "serviceLevel must be between 0.50 and 0.999")
    @Builder.Default
    double serviceLevel = 0.95;

    @DecimalMin(value = "0.0", message = "minOrderQuantity must be >= 0")
    @Builder.Default
    Double minOrderQuantity = 0.0;

    @DecimalMin(value = "0.0", message = "maxOrderQuantity must be >= 0")
    Double maxOrderQuantity;

    @DecimalMin(value = "0.0", message = "orderMultiple must be >= 0")
    Double orderMultiple;

    @Size(max = 5000, message = "demandHistory supports up to 5000 values")
    List<@DecimalMin(value = "0.0", message = "demandHistory values must be >= 0") Double> demandHistory;

    @DecimalMin(value = "0.0", message = "forecastMeanDaily must be >= 0")
    Double forecastMeanDaily;

    @DecimalMin(value = "0.0", message = "forecastStdDaily must be >= 0")
    Double forecastStdDaily;

    @Size(max = 3650, message = "demandForecast supports up to 3650 values")
    List<@DecimalMin(value = "0.0", message = "demandForecast values must be >= 0") Double> demandForecast;

    @DecimalMin(value = "0.0", message = "unitCost must be >= 0")
    Double unitCost;

    @DecimalMin(value = "0.0", message = "holdingCostPerUnitPerDay must be >= 0")
    Double holdingCostPerUnitPerDay;

    @DecimalMin(value = "0.0", message = "stockoutCostPerUnit must be >= 0")
    Double stockoutCostPerUnit;
}
