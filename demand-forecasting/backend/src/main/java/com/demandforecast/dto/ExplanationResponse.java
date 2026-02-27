package com.demandforecast.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class ExplanationResponse {
    UUID predictionId;
    double demand;
    String method;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant generatedAt;
    List<FeatureContribution> contributions;

    @Value
    @Builder
    public static class FeatureContribution {
        String feature;
        Double value;
        Double baseline;
        Double importance;
        Double contribution;
    }
}
