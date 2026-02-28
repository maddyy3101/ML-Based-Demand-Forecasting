package com.powergrid.forecasting.service;

import com.powergrid.forecasting.dto.BacktestRequest;
import com.powergrid.forecasting.dto.BacktestResultDto;
import com.powergrid.forecasting.dto.JobStatusDto;
import com.powergrid.forecasting.entity.ProcurementForecast;
import com.powergrid.forecasting.repository.ProcurementForecastRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BacktestService {

    private final AsyncJobService asyncJobService;
    private final ProcurementForecastRepository forecastRepository;

    public BacktestService(AsyncJobService asyncJobService, ProcurementForecastRepository forecastRepository) {
        this.asyncJobService = asyncJobService;
        this.forecastRepository = forecastRepository;
    }

    public String submit(BacktestRequest request, String username) {
        return asyncJobService.submit("BACKTEST", "Backtest queued", username, () -> runBacktest(request));
    }

    public JobStatusDto get(String jobId) {
        return asyncJobService.get(jobId);
    }

    private BacktestResultDto runBacktest(BacktestRequest request) {
        List<ProcurementForecast> records = forecastRepository.findAll().stream()
                .filter(f -> f.getActualQuantity() != null)
                .limit(Math.max(1, request.sampleSize()))
                .toList();

        if (records.isEmpty()) {
            return new BacktestResultDto("N/A", 0, 0, 0, "No actual-labelled forecasts available");
        }

        double mae = records.stream().mapToDouble(r -> Math.abs(r.getPredictedQuantity() - r.getActualQuantity())).average().orElse(0.0);
        double rmse = Math.sqrt(records.stream()
                .mapToDouble(r -> Math.pow(r.getPredictedQuantity() - r.getActualQuantity(), 2))
                .average().orElse(0.0));
        double mape = records.stream().mapToDouble(r ->
                Math.abs(r.getPredictedQuantity() - r.getActualQuantity()) / Math.max(r.getActualQuantity(), 1.0)
        ).average().orElse(0.0) * 100.0;

        return new BacktestResultDto("N/A", round(mae), round(rmse), round(mape), "Backtest completed");
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
