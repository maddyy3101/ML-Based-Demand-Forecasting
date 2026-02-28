package com.powergrid.forecasting.exception;

public class MlServiceException extends PowerGridException {
    public MlServiceException(String message) {
        super(message);
    }

    public MlServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
