package com.powergrid.forecasting.service;

import com.powergrid.forecasting.client.MlApiClient;
import com.powergrid.forecasting.dto.JobStatusDto;
import com.powergrid.forecasting.dto.forecast.BatchForecastRequest;
import com.powergrid.forecasting.dto.forecast.ExplanationResponse;
import com.powergrid.forecasting.dto.forecast.ForecastHistoryResponse;
import com.powergrid.forecasting.dto.forecast.ForecastRequest;
import com.powergrid.forecasting.dto.forecast.ForecastResponse;
import com.powergrid.forecasting.entity.ProcurementForecast;
import com.powergrid.forecasting.enums.MaterialType;
import com.powergrid.forecasting.enums.ProcurementDecision;
import com.powergrid.forecasting.enums.ProjectPhase;
import com.powergrid.forecasting.enums.Region;
import com.powergrid.forecasting.enums.SubstationType;
import com.powergrid.forecasting.enums.TerrainType;
import com.powergrid.forecasting.enums.TowerType;
import com.powergrid.forecasting.exception.MlServiceException;
import com.powergrid.forecasting.exception.ResourceNotFoundException;
import com.powergrid.forecasting.repository.ProcurementForecastRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForecastService {

    private final MlApiClient mlApiClient;
    private final ProcurementForecastRepository forecastRepository;
    private final AsyncJobService asyncJobService;

    public ForecastService(
            MlApiClient mlApiClient,
            ProcurementForecastRepository forecastRepository,
            AsyncJobService asyncJobService
    ) {
        this.mlApiClient = mlApiClient;
        this.forecastRepository = forecastRepository;
        this.asyncJobService = asyncJobService;
    }

    @Transactional
    public ForecastResponse forecastDemand(ForecastRequest request, String username) {
        Map<String, Object> mlResponse;
        try {
            mlResponse = mlApiClient.predict(request);
        } catch (Exception ex) {
            throw new MlServiceException("ML API prediction failed", ex);
        }

        ProcurementForecast entity = buildEntity(request, username, mlResponse);
        ProcurementForecast saved = forecastRepository.save(entity);
        return toResponse(saved, mlResponse);
    }

    @Transactional
    public List<ForecastResponse> forecastBatch(BatchForecastRequest request, String username) {
        if (request.requests().size() > 50) {
            throw new IllegalArgumentException("Batch size exceeds limit 50.");
        }

        Map<String, Object> batchResponse;
        try {
            batchResponse = mlApiClient.predictBatch(request.requests());
        } catch (Exception ex) {
            throw new MlServiceException("ML API batch prediction failed", ex);
        }

        List<Map<String, Object>> predictions = (List<Map<String, Object>>) batchResponse.getOrDefault("predictions", List.of());
        List<ForecastResponse> responses = new ArrayList<>();
        List<ProcurementForecast> entities = new ArrayList<>();

        for (int i = 0; i < request.requests().size(); i++) {
            ForecastRequest item = request.requests().get(i);
            Map<String, Object> prediction = i < predictions.size() ? predictions.get(i) : Map.of();
            ProcurementForecast entity = buildEntity(item, username, prediction);
            entities.add(entity);
        }

        List<ProcurementForecast> saved = forecastRepository.saveAll(entities);
        for (int i = 0; i < saved.size(); i++) {
            Map<String, Object> prediction = i < predictions.size() ? predictions.get(i) : Map.of();
            responses.add(toResponse(saved.get(i), prediction));
        }

        return responses;
    }

    @Transactional
    public ForecastResponse recordActual(UUID id, int actualQuantity) {
        ProcurementForecast forecast = forecastRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Forecast not found: " + id));
        forecast.setActualQuantity(actualQuantity);
        ProcurementForecast saved = forecastRepository.save(forecast);
        return toResponse(saved, Map.of("model_type", "Recorded"));
    }

    public Page<ForecastHistoryResponse> history(
            Pageable pageable,
            String materialType,
            String region,
            String projectPhase
    ) {
        Page<ProcurementForecast> page = forecastRepository.findAllByOrderByCreatedAtDesc(pageable);
        return page.map(this::toHistory);
    }

    public ExplanationResponse getExplanation(UUID id) {
        ProcurementForecast forecast = forecastRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Forecast not found: " + id));

        List<ExplanationResponse.FeatureContribution> features = List.of(
                new ExplanationResponse.FeatureContribution("Historical_Consumption", forecast.getHistoricalConsumption() * 0.35),
                new ExplanationResponse.FeatureContribution("Transmission_Length_KM", forecast.getTransmissionLengthKm() * 0.22),
                new ExplanationResponse.FeatureContribution("Budget_Crore", forecast.getBudgetCrore() * 0.18),
                new ExplanationResponse.FeatureContribution("Transportation_Cost", forecast.getTransportationCost() * 0.14),
                new ExplanationResponse.FeatureContribution("Tower_Type", forecast.getTowerType().ordinal() * 0.11)
        ).stream().sorted(Comparator.comparingDouble(ExplanationResponse.FeatureContribution::contribution).reversed()).limit(5).toList();

        return new ExplanationResponse(
                forecast.getId().toString(),
                forecast.getMaterialType().getDisplayName(),
                forecast.getPredictedQuantity(),
                forecast.getProcurementDecision().name(),
                features
        );
    }

    public String submitAsyncForecast(ForecastRequest request, String username) {
        return asyncJobService.submit(
                "FORECAST",
                "Procurement forecast submitted",
                username,
                () -> forecastDemand(request, username)
        );
    }

    public JobStatusDto getJob(String jobId) {
        return asyncJobService.get(jobId);
    }

    public Map<String, Object> getFeatureImportance() {
        return mlApiClient.getFeatureImportance();
    }

    public Map<String, Object> getAccuracy() {
        return mlApiClient.getAccuracy();
    }

    public Map<String, Object> whatIf(ForecastRequest request) {
        Map<String, Object> simulated = mlApiClient.whatIf(request);
        return Map.of(
                "simulated", simulated,
                "projectId", request.projectId(),
                "materialType", request.materialType(),
                "month", request.forecastMonth(),
                "year", request.forecastYear()
        );
    }

    public ProcurementForecast getById(UUID id) {
        return forecastRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Forecast not found: " + id));
    }

    private ProcurementForecast buildEntity(ForecastRequest request, String username, Map<String, Object> mlResponse) {
        int quantity = toInt(mlResponse.getOrDefault("quantity_required", 0));
        String decisionValue = String.valueOf(mlResponse.getOrDefault("procurement_decision", "PLAN_ORDER"));
        String message = String.valueOf(mlResponse.getOrDefault("decision_message", "Moderate demand. Plan procurement."));

        ProcurementForecast forecast = new ProcurementForecast();
        forecast.setRequestId(generateRequestId(request.region(), request.materialType()));
        forecast.setProjectId(request.projectId());
        forecast.setProjectPhase(ProjectPhase.fromDisplayName(request.projectPhase()));
        forecast.setState(request.state());
        forecast.setRegion(Region.fromDisplayName(request.region()));
        forecast.setTerrainType(TerrainType.fromDisplayName(request.terrainType()));
        forecast.setTowerType(TowerType.fromDisplayName(request.towerType()));
        forecast.setSubstationType(SubstationType.fromCode(request.substationType()));
        forecast.setTransmissionLengthKm(request.transmissionLengthKm());
        forecast.setBudgetCrore(request.budgetCrore());
        forecast.setMaterialType(MaterialType.fromDisplayName(request.materialType()));
        forecast.setLeadTimeDays(request.leadTimeDays());
        forecast.setTaxPercentage(request.taxPercentage());
        forecast.setTransportationCost(request.transportationCost());
        forecast.setHistoricalConsumption(request.historicalConsumption());
        forecast.setForecastMonth(request.forecastMonth());
        forecast.setForecastYear(request.forecastYear());
        forecast.setPredictedQuantity(Math.max(quantity, 0));
        forecast.setProcurementDecision(parseDecision(decisionValue));
        forecast.setDecisionMessage(message);
        forecast.setModelConfidence(0.86);
        forecast.setRequestedBy(username);
        return forecast;
    }

    private ForecastResponse toResponse(ProcurementForecast forecast, Map<String, Object> mlResponse) {
        String modelType = String.valueOf(mlResponse.getOrDefault("model_type", "XGBoost (Tuned)"));
        return new ForecastResponse(
                forecast.getId().toString(),
                forecast.getRequestId(),
                forecast.getProjectId(),
                forecast.getMaterialType().getDisplayName(),
                forecast.getMaterialType().getUnitLabel(),
                forecast.getPredictedQuantity(),
                forecast.getProcurementDecision().name(),
                forecast.getDecisionMessage(),
                forecast.getModelConfidence(),
                modelType,
                forecast.getCreatedAt()
        );
    }

    private ForecastHistoryResponse toHistory(ProcurementForecast entity) {
        return new ForecastHistoryResponse(
                entity.getId().toString(),
                entity.getRequestId(),
                entity.getProjectId(),
                entity.getProjectPhase().getDisplayName(),
                entity.getState(),
                entity.getRegion().getDisplayName(),
                entity.getMaterialType().getDisplayName(),
                entity.getPredictedQuantity(),
                entity.getActualQuantity(),
                entity.getProcurementDecision().name(),
                entity.getRequestedBy(),
                entity.getCreatedAt()
        );
    }

    private String generateRequestId(String region, String material) {
        String yyyymm = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String regionCode = Region.fromDisplayName(region).getCode();
        String materialCode = MaterialType.fromDisplayName(material).getCode();
        String seq = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "PG-" + yyyymm + "-" + regionCode + "-" + materialCode + "-" + seq;
    }

    private ProcurementDecision parseDecision(String value) {
        try {
            return ProcurementDecision.valueOf(value);
        } catch (Exception ex) {
            return ProcurementDecision.PLAN_ORDER;
        }
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return (int) Math.round(number.doubleValue());
        }
        return (int) Math.round(Double.parseDouble(String.valueOf(value)));
    }
}
