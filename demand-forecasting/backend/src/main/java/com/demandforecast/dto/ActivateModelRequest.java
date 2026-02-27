package com.demandforecast.dto;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

@Value
@Builder
@Jacksonized
public class ActivateModelRequest {
    String activatedBy;
    Map<String, Object> metadata;
}
