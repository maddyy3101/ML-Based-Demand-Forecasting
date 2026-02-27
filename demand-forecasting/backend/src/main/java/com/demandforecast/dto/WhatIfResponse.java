package com.demandforecast.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class WhatIfResponse {
    double baseDemand;
    Double baseLowerBound;
    Double baseUpperBound;
    List<ScenarioResult> scenarios;

    @Value
    @Builder
    public static class ScenarioResult {
        String name;
        double demand;
        Double lowerBound;
        Double upperBound;
        double demandDelta;
    }
}
