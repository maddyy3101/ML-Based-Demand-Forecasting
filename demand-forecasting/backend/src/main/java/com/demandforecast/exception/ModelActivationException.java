package com.demandforecast.exception;

public class ModelActivationException extends DemandForecastException {
    public ModelActivationException(String message) {
        super("MODEL_ACTIVATION_ERROR", message);
    }
}
