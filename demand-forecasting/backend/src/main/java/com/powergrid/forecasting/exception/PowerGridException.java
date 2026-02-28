package com.powergrid.forecasting.exception;

public class PowerGridException extends RuntimeException {
    public PowerGridException(String message) {
        super(message);
    }

    public PowerGridException(String message, Throwable cause) {
        super(message, cause);
    }
}
