package com.demandforecast.exception;

import java.util.UUID;

public class JobNotFoundException extends DemandForecastException {
    public JobNotFoundException(UUID jobId) {
        super("JOB_NOT_FOUND", "Job with id '" + jobId + "' not found.");
    }
}
