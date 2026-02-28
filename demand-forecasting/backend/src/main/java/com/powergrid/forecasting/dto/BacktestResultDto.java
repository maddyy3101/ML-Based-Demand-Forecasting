package com.powergrid.forecasting.dto;

public record BacktestResultDto(
        String jobId,
        double mae,
        double rmse,
        double mape,
        String summary
) {
}
