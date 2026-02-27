package com.demandforecast.service;

import com.demandforecast.dto.AsyncJobResponse;
import com.demandforecast.dto.AsyncJobStatus;
import com.demandforecast.exception.JobNotFoundException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@Service
public class AsyncJobService {

    @Value("${jobs.pool-size:4}")
    private int poolSize;

    @Value("${jobs.max-retained:1000}")
    private int maxRetained;

    private ExecutorService executor;
    private final ConcurrentHashMap<UUID, JobState> jobs = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        executor = Executors.newFixedThreadPool(Math.max(2, poolSize));
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    public UUID submit(String jobType, String requestId, Supplier<Object> task) {
        UUID jobId = UUID.randomUUID();
        JobState state = JobState.queued(jobId, jobType, requestId);
        jobs.put(jobId, state);
        cleanupIfNeeded();

        CompletableFuture.runAsync(() -> execute(state, task), executor);
        return jobId;
    }

    public AsyncJobResponse getJob(UUID jobId) {
        JobState state = jobs.get(jobId);
        if (state == null) {
            throw new JobNotFoundException(jobId);
        }
        return state.toResponse();
    }

    private void execute(JobState state, Supplier<Object> task) {
        state.markRunning("Job started", 5);
        try {
            Object result = task.get();
            state.markCompleted(result, "Job completed", 100);
        } catch (Exception ex) {
            state.markFailed(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    private void cleanupIfNeeded() {
        if (jobs.size() <= maxRetained) {
            return;
        }
        jobs.entrySet().stream()
            .filter(e -> e.getValue().status == AsyncJobStatus.COMPLETED || e.getValue().status == AsyncJobStatus.FAILED)
            .sorted(Comparator.comparing(e -> e.getValue().createdAt))
            .limit(Math.max(1, jobs.size() - maxRetained))
            .map(Map.Entry::getKey)
            .forEach(jobs::remove);
    }

    private static final class JobState {
        private final UUID jobId;
        private final String jobType;
        private final String requestId;
        private final Instant createdAt;
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile AsyncJobStatus status;
        private volatile Integer progressPercent;
        private volatile String message;
        private volatile Object result;

        private JobState(UUID jobId, String jobType, String requestId, Instant createdAt) {
            this.jobId = jobId;
            this.jobType = jobType;
            this.requestId = requestId;
            this.createdAt = createdAt;
            this.status = AsyncJobStatus.QUEUED;
            this.progressPercent = 0;
            this.message = "Queued";
        }

        private static JobState queued(UUID id, String type, String requestId) {
            return new JobState(id, type, requestId, Instant.now());
        }

        private synchronized void markRunning(String message, int progress) {
            this.startedAt = Instant.now();
            this.status = AsyncJobStatus.RUNNING;
            this.message = message;
            this.progressPercent = progress;
        }

        private synchronized void markCompleted(Object result, String message, int progress) {
            this.completedAt = Instant.now();
            this.status = AsyncJobStatus.COMPLETED;
            this.result = result;
            this.message = message;
            this.progressPercent = progress;
        }

        private synchronized void markFailed(String message) {
            this.completedAt = Instant.now();
            this.status = AsyncJobStatus.FAILED;
            this.message = message;
            this.progressPercent = 100;
        }

        private AsyncJobResponse toResponse() {
            return AsyncJobResponse.builder()
                .jobId(jobId)
                .jobType(jobType)
                .status(status)
                .createdAt(createdAt)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .progressPercent(progressPercent)
                .message(message)
                .result(result)
                .requestId(requestId)
                .build();
        }
    }
}
