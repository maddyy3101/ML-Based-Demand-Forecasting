package com.demandforecast.exception;

public class MlApiException extends DemandForecastException {
    public MlApiException(String message) {
        super("ML_API_ERROR", message);
    }
    public MlApiException(String message, Throwable cause) {
        super("ML_API_ERROR", message, cause);
    }
}
