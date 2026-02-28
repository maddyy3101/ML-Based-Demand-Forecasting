package com.powergrid.forecasting.dto.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MovementRequest(
        @NotBlank String inventoryId,
        @NotBlank String movementType,
        @NotNull @Min(1) Integer quantity,
        String reason,
        String projectId,
        String vendorName,
        String invoiceNumber,
        String notes
) {
}
