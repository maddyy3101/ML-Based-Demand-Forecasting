package com.powergrid.forecasting.dto.admin;

import com.powergrid.forecasting.enums.Role;
import jakarta.validation.constraints.NotNull;

public record UserUpdateRequest(
        @NotNull Role role,
        String assignedRegion,
        @NotNull Boolean active
) {
}
