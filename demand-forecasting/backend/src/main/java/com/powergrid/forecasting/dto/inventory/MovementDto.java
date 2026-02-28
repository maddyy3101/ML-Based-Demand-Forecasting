package com.powergrid.forecasting.dto.inventory;

import java.time.Instant;

public record MovementDto(
        String id,
        String materialName,
        String movementType,
        int quantity,
        String reason,
        String projectId,
        String vendorName,
        String performedBy,
        Instant timestamp
) {
}
