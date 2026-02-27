package com.demandforecast.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class BacktestReportResponse {
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
    List<SegmentMetrics> segments;

    @Value
    @Builder
    public static class SegmentMetrics {
        String category;
        String region;
        long sampleCount;
        Double mae;
        Double rmse;
        Double mape;
    }
}
