package com.demandforecast.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

@Value
@Builder
public class ModelVersionResponse {
    String version;
    boolean active;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant activatedAt;
    String activatedBy;
    Map<String, Object> metadata;
}
