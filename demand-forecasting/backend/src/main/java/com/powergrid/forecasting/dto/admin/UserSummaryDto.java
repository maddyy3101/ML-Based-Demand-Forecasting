package com.powergrid.forecasting.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record UserSummaryDto(
        UUID id,
        String username,
        String email,
        String role,
        String assignedRegion,
        String employeeId,
        boolean active,
        Instant createdAt
) {
}
