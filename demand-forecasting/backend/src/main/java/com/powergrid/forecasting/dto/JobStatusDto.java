package com.powergrid.forecasting.dto;

public record JobStatusDto(
        String jobId,
        String type,
        String status,
        String message,
        Object result
) {
}
