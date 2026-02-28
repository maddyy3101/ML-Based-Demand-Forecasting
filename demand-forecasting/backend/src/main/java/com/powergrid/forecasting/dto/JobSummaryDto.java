package com.powergrid.forecasting.dto;

import java.time.Instant;

public record JobSummaryDto(
        String jobId,
        String type,
        String status,
        String message,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
