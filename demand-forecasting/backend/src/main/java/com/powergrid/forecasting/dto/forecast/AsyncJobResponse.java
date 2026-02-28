package com.powergrid.forecasting.dto.forecast;

public record AsyncJobResponse(
        String jobId,
        String status,
        String message
) {
}
