package com.demandforecast.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Value
@Builder
public class ForecastResponse {
    UUID   predictionId;
    double demand;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
    LocalDate forecastDate;
    String category;
    String region;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant createdAt;
    String requestId;
}
