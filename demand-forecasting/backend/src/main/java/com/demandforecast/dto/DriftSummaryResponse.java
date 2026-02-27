package com.demandforecast.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class DriftSummaryResponse {
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate baselineFrom;
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate baselineTo;
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate recentFrom;
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate recentTo;
    long baselineSampleSize;
    long recentSampleSize;
    Double predictionDriftScore;
    boolean predictionDriftDetected;
    List<FeatureDrift> featureDrift;

    @Value
    @Builder
    public static class FeatureDrift {
        String feature;
        Double baselineMean;
        Double recentMean;
        Double driftScore;
        boolean driftDetected;
    }
}
