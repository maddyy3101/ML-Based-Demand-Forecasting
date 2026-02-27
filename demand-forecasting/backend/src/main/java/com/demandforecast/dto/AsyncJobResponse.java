package com.demandforecast.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AsyncJobResponse {
    UUID jobId;
    String jobType;
    AsyncJobStatus status;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant startedAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant completedAt;
    Integer progressPercent;
    String message;
    Object result;
    String requestId;
}
