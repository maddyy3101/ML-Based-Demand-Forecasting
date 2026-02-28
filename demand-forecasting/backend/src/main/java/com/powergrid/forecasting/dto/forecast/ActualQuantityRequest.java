package com.powergrid.forecasting.dto.forecast;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ActualQuantityRequest(
        @NotNull @Min(0) Integer actualQuantity
) {
}
