package com.powergrid.forecasting.dto.admin;

import java.time.Instant;

public record RetrainingStatusDto(
        String jobId,
        String status,
        Instant startedAt,
        Instant completedAt,
        int rowCount,
        String logOutput,
        String modelMetrics
) {
}
