package com.powergrid.forecasting.dto.admin;

public record ProcDocStatusDto(
        boolean configured,
        String source,
        String maskedKey,
        String statusMessage
) {
}

