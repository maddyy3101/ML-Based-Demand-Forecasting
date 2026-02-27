package com.demandforecast.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import java.time.Instant;
import java.util.List;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {
    int    status;
    String error;
    String message;
    String path;
    String requestId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant timestamp;
    List<FieldError> fieldErrors;

    @Value
    @Builder
    public static class FieldError {
        String field;
        Object rejectedValue;
        String message;
    }
}
