package com.powergrid.forecasting.dto.forecast;

import java.util.List;

public record ExplanationResponse(
        String forecastId,
        String materialType,
        int predictedQuantity,
        String procurementDecision,
        List<FeatureContribution> topFeatures
) {
    public record FeatureContribution(String feature, double contribution) {
    }
}
