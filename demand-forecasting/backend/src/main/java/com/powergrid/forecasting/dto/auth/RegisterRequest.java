package com.powergrid.forecasting.dto.auth;

import com.powergrid.forecasting.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 4, max = 50) String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 120) String password,
        @NotNull Role role,
        String assignedRegion,
        String employeeId
) {
}
