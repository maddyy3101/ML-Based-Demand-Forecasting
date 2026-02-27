package com.demandforecast.exception;

import java.util.UUID;

public class PredictionNotFoundException extends DemandForecastException {
    public PredictionNotFoundException(UUID id) {
        super("PREDICTION_NOT_FOUND", "Prediction with id '" + id + "' not found.");
    }
}
