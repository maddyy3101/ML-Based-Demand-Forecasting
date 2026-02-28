package com.powergrid.forecasting.dto;

import jakarta.validation.constraints.NotBlank;

public record PurchasePlanRequestDto(
        @NotBlank String planMonth,
        String region
) {
}
