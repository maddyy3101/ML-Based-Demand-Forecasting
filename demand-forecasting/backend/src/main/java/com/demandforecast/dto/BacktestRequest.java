package com.demandforecast.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;

@Value
@Builder
@Jacksonized
public class BacktestRequest {
    @NotNull(message = "fromDate is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate fromDate;

    @NotNull(message = "toDate is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate toDate;

    String category;
    String region;
}
