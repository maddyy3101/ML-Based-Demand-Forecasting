package com.demandforecast.exception;

import lombok.Getter;

@Getter
public abstract class DemandForecastException extends RuntimeException {
    private final String errorCode;
    protected DemandForecastException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    protected DemandForecastException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
