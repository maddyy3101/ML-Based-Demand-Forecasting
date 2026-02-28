package com.powergrid.forecasting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReplenishmentRequestDto(
        @NotNull List<Item> items,
        @NotBlank String planningHorizon
) {
    public record Item(String inventoryId, int leadTimeDays, double serviceLevel) {
    }
}
