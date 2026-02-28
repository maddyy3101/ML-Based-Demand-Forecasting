package com.powergrid.forecasting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergrid.forecasting.client.MlApiClient;
import com.powergrid.forecasting.entity.RetrainingJob;
import com.powergrid.forecasting.enums.RetrainingStatus;
import com.powergrid.forecasting.repository.RetrainingJobRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class RetrainingExecutorService {

    private final MlApiClient mlApiClient;
    private final RetrainingJobRepository retrainingJobRepository;
    private final ObjectMapper objectMapper;

    public RetrainingExecutorService(
            MlApiClient mlApiClient,
            RetrainingJobRepository retrainingJobRepository,
            ObjectMapper objectMapper
    ) {
        this.mlApiClient = mlApiClient;
        this.retrainingJobRepository = retrainingJobRepository;
        this.objectMapper = objectMapper;
    }

    @Async
    public void executeRetraining(RetrainingJob job) {
        try {
            job.setStatus(RetrainingStatus.RUNNING);
            job.setStartedAt(Instant.now());
            job.setLogOutput("Retraining started for dataset: " + job.getDatasetPath());
            retrainingJobRepository.save(job);

            Map<String, Object> retrainResponse = mlApiClient.retrain(job.getDatasetPath());
            Map<String, Object> accuracy = mlApiClient.getAccuracy();

            job.setStatus(RetrainingStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            job.setLogOutput(job.getLogOutput() + "\nML API response: " + retrainResponse);
            job.setModelMetricsJson(objectMapper.writeValueAsString(accuracy));
            retrainingJobRepository.save(job);
        } catch (Exception ex) {
            job.setStatus(RetrainingStatus.FAILED);
            job.setCompletedAt(Instant.now());
            job.setLogOutput((job.getLogOutput() == null ? "" : job.getLogOutput() + "\n") + "Error: " + ex.getMessage());
            retrainingJobRepository.save(job);
        }
    }
}
