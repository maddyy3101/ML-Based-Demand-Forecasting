package com.demandforecast.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
@Jacksonized
public class WhatIfRequest {
    @NotNull(message = "base forecast request is required")
    @Valid
    ForecastRequest base;

    @NotEmpty(message = "at least one scenario is required")
    @Valid
    List<ScenarioOverride> scenarios;

    @Value
    @Builder
    @Jacksonized
    public static class ScenarioOverride {
        String name;
        LocalDate date;
        String category;
        String region;
        Integer inventoryLevel;
        Integer unitsSold;
        Integer unitsOrdered;
        Double price;
        Double discount;
        String weatherCondition;
        Boolean promotion;
        Double competitorPricing;
        String seasonality;
        Boolean epidemic;
    }
}
