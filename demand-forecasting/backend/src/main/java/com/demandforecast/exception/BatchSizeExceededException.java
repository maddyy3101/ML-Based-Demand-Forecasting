package com.demandforecast.exception;

public class BatchSizeExceededException extends DemandForecastException {
    public BatchSizeExceededException(int size, int max) {
        super("BATCH_SIZE_EXCEEDED",
              "Batch size " + size + " exceeds the maximum allowed size of " + max + ".");
    }
}
