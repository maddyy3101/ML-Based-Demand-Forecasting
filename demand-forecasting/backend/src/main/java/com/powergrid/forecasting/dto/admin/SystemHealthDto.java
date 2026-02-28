package com.powergrid.forecasting.dto.admin;

import java.time.Instant;

public record SystemHealthDto(
        String flaskApiStatus,
        String dbStatus,
        String activeModelType,
        Double modelRmse,
        Double modelMae,
        Instant lastRetrainedAt,
        long totalForecastsToday,
        long totalForecastsAllTime
) {
}
