package com.powergrid.forecasting.dto.admin;

public record UploadResponseDto(
        String jobId,
        String fileName,
        int rowCount,
        String status,
        String message
) {
}
