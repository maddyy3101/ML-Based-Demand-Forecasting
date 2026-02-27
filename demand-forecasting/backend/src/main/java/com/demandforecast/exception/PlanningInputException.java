package com.demandforecast.exception;

public class PlanningInputException extends DemandForecastException {
    public PlanningInputException(String message) {
        super("PLANNING_INPUT_ERROR", message);
    }
}
