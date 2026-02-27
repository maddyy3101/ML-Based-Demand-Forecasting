package com.demandforecast.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Value
@Builder
@Jacksonized
public class ReplenishmentPlanRequest {

    @NotEmpty(message = "items must not be empty")
    @Valid
    List<@Valid PlanningItemRequest> items;

    @DecimalMin(value = "1.0", message = "overstockFactor must be >= 1")
    @Builder.Default
    double overstockFactor = 1.8;
}
