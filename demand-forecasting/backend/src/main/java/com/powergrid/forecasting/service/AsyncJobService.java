package com.powergrid.forecasting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergrid.forecasting.dto.JobStatusDto;
import com.powergrid.forecasting.dto.JobSummaryDto;
import com.powergrid.forecasting.entity.AsyncJobRecord;
import com.powergrid.forecasting.exception.ResourceNotFoundException;
import com.powergrid.forecasting.repository.AsyncJobRecordRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncJobService {

    private final Map<String, JobStatusDto> jobs = new ConcurrentHashMap<>();
    private final AsyncJobRecordRepository asyncJobRecordRepository;
    private final ObjectMapper objectMapper;

    public AsyncJobService(AsyncJobRecordRepository asyncJobRecordRepository, ObjectMapper objectMapper) {
        this.asyncJobRecordRepository = asyncJobRecordRepository;
        this.objectMapper = objectMapper;
    }

    public String submit(String type, String message, Supplier<Object> task) {
        return submit(type, message, null, task);
    }

    public String submit(String type, String message, String createdBy, Supplier<Object> task) {
        String jobId = UUID.randomUUID().toString();
        JobStatusDto pending = new JobStatusDto(jobId, type, "PENDING", message, null);
        jobs.put(jobId, pending);
        save(jobId, type, "PENDING", message, null, createdBy);
        runAsync(jobId, type, createdBy, task);
        return jobId;
    }

    public JobStatusDto get(String jobId) {
        JobStatusDto status = jobs.get(jobId);
        if (status != null) {
            return status;
        }
        return asyncJobRecordRepository.findById(jobId)
                .map(this::toJobStatus)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
    }

    public List<JobSummaryDto> list(String username, boolean includeAll) {
        List<AsyncJobRecord> rows = includeAll
                ? asyncJobRecordRepository.findTop100ByOrderByCreatedAtDesc()
                : asyncJobRecordRepository.findTop100ByCreatedByOrderByCreatedAtDesc(username);
        return rows.stream().map(this::toSummary).toList();
    }

    @Async
    public void runAsync(String jobId, String type, String createdBy, Supplier<Object> task) {
        jobs.put(jobId, new JobStatusDto(jobId, type, "RUNNING", "Job is running", null));
        save(jobId, type, "RUNNING", "Job is running", null, createdBy);
        try {
            Object result = task.get();
            jobs.put(jobId, new JobStatusDto(jobId, type, "COMPLETED", "Job completed", result));
            save(jobId, type, "COMPLETED", "Job completed", result, createdBy);
        } catch (Exception ex) {
            jobs.put(jobId, new JobStatusDto(jobId, type, "FAILED", ex.getMessage(), null));
            save(jobId, type, "FAILED", ex.getMessage(), null, createdBy);
        }
    }

    private void save(String jobId, String type, String status, String message, Object result, String createdBy) {
        AsyncJobRecord row = asyncJobRecordRepository.findById(jobId).orElseGet(AsyncJobRecord::new);
        row.setId(jobId);
        row.setType(type);
        row.setStatus(status);
        row.setMessage(message);
        row.setCreatedBy(createdBy);
        row.setResultJson(toJson(result));
        asyncJobRecordRepository.save(row);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private Object fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, Object.class);
        } catch (Exception ex) {
            return raw;
        }
    }

    private JobStatusDto toJobStatus(AsyncJobRecord row) {
        return new JobStatusDto(
                row.getId(),
                row.getType(),
                row.getStatus(),
                row.getMessage(),
                fromJson(row.getResultJson())
        );
    }

    private JobSummaryDto toSummary(AsyncJobRecord row) {
        return new JobSummaryDto(
                row.getId(),
                row.getType(),
                row.getStatus(),
                row.getMessage(),
                row.getCreatedBy(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }
}
