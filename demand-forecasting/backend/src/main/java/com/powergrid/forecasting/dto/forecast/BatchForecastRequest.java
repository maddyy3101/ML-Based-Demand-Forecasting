package com.powergrid.forecasting.dto.forecast;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchForecastRequest(
        @NotNull @Size(min = 1, max = 50) List<@Valid ForecastRequest> requests
) {
}
