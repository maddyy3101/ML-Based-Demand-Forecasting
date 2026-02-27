package com.demandforecast.exception;

public class InvalidBacktestRequestException extends DemandForecastException {
    public InvalidBacktestRequestException(String message) {
        super("INVALID_BACKTEST_REQUEST", message);
    }
}
