package com.demandforecast.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class AccuracyMetricsResponse {
    long sampleCount;
    Double mae;
    Double rmse;
    Double mape;
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate fromDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate toDate;
    String category;
    String region;
}
