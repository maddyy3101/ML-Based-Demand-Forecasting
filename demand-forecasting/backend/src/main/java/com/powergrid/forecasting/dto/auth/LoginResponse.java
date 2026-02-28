package com.powergrid.forecasting.dto.auth;

import java.util.UUID;

public record LoginResponse(
        String token,
        String role,
        String username,
        UUID userId,
        String assignedRegion,
        String employeeId,
        long expiresIn
) {
}
