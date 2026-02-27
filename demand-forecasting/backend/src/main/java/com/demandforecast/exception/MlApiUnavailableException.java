package com.demandforecast.exception;

public class MlApiUnavailableException extends DemandForecastException {
    public MlApiUnavailableException(Throwable cause) {
        super("ML_API_UNAVAILABLE",
              "The ML inference service is currently unavailable. Please try again later.",
              cause);
    }
}
