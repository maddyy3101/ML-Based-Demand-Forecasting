package com.powergrid.forecasting.dto;

public record PlanningExceptionDto(
        String materialType,
        String region,
        String stockStatus,
        String urgency,
        String message
) {
}
