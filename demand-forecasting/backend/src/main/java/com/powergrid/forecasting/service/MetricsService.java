package com.powergrid.forecasting.service;

import com.powergrid.forecasting.entity.ProcurementForecast;
import com.powergrid.forecasting.enums.MaterialType;
import com.powergrid.forecasting.enums.Region;
import com.powergrid.forecasting.repository.ProcurementForecastRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

    private final ProcurementForecastRepository forecastRepository;

    public MetricsService(ProcurementForecastRepository forecastRepository) {
        this.forecastRepository = forecastRepository;
    }

    public Map<String, Object> performance(String from, String to, String materialType, String region) {
        List<ProcurementForecast> all = forecastRepository.findAll();
        List<ProcurementForecast> filtered = all.stream()
                .filter(f -> f.getActualQuantity() != null)
                .filter(f -> materialType == null || materialType.isBlank() || f.getMaterialType() == MaterialType.fromDisplayName(materialType))
                .filter(f -> region == null || region.isBlank() || f.getRegion() == Region.fromDisplayName(region))
                .toList();

        if (filtered.isEmpty()) {
            return Map.of("count", 0, "MAE", 0.0, "RMSE", 0.0, "MAPE", 0.0);
        }

        double mae = filtered.stream().mapToDouble(f -> Math.abs(f.getPredictedQuantity() - f.getActualQuantity())).average().orElse(0.0);
        double rmse = Math.sqrt(filtered.stream()
                .mapToDouble(f -> Math.pow(f.getPredictedQuantity() - f.getActualQuantity(), 2))
                .average().orElse(0.0));
        double mape = filtered.stream()
                .mapToDouble(f -> {
                    double actual = Math.max(f.getActualQuantity(), 1);
                    return Math.abs(f.getPredictedQuantity() - f.getActualQuantity()) / actual;
                })
                .average().orElse(0.0) * 100.0;

        Map<String, Object> response = new HashMap<>();
        response.put("count", filtered.size());
        response.put("MAE", round(mae));
        response.put("RMSE", round(rmse));
        response.put("MAPE", round(mape));
        response.put("from", from);
        response.put("to", to);
        response.put("materialType", materialType);
        response.put("region", region);
        return response;
    }

    public Map<String, Object> driftSummary() {
        Instant boundary = Instant.now().minusSeconds(30L * 24L * 60L * 60L);
        List<ProcurementForecast> recent = forecastRepository.findAll().stream()
                .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().isAfter(boundary))
                .toList();
        List<ProcurementForecast> previous = forecastRepository.findAll().stream()
                .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().isBefore(boundary))
                .toList();

        double recentMeanHist = recent.stream().mapToDouble(ProcurementForecast::getHistoricalConsumption).average().orElse(0.0);
        double prevMeanHist = previous.stream().mapToDouble(ProcurementForecast::getHistoricalConsumption).average().orElse(0.0);
        double featureDrift = prevMeanHist == 0.0 ? 0.0 : ((recentMeanHist - prevMeanHist) / prevMeanHist) * 100.0;

        double recentPred = recent.stream().mapToDouble(ProcurementForecast::getPredictedQuantity).average().orElse(0.0);
        double prevPred = previous.stream().mapToDouble(ProcurementForecast::getPredictedQuantity).average().orElse(0.0);
        double predictionDrift = prevPred == 0.0 ? 0.0 : ((recentPred - prevPred) / prevPred) * 100.0;

        return Map.of(
                "feature_drift_percent", round(featureDrift),
                "prediction_drift_percent", round(predictionDrift),
                "recent_window_size", recent.size(),
                "previous_window_size", previous.size()
        );
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
