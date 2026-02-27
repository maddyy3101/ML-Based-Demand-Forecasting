package com.demandforecast.service;

import com.demandforecast.client.MlApiClient;
import com.demandforecast.dto.ForecastRequest;
import com.demandforecast.dto.ForecastResponse;
import com.demandforecast.entity.PredictionRecord;
import com.demandforecast.exception.BatchSizeExceededException;
import com.demandforecast.exception.PredictionNotFoundException;
import com.demandforecast.repository.PredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final MlApiClient          mlApiClient;
    private final PredictionRepository repository;

    @Value("${ml.api.max-batch-size:256}")
    private int maxBatchSize;

    @Transactional
    public Mono<ForecastResponse> forecast(ForecastRequest req, String requestId) {
        return mlApiClient.predict(toPayload(req), requestId)
            .map(prediction -> {
                PredictionRecord saved = persist(req, prediction, requestId);
                log.info("Forecast saved | id={} | demand={} | requestId={}",
                         saved.getId(), prediction.demand(), requestId);
                return toResponse(saved, requestId);
            });
    }

    @Transactional
    public Mono<List<ForecastResponse>> forecastBatch(List<ForecastRequest> requests, String requestId) {
        if (requests.size() > maxBatchSize) {
            throw new BatchSizeExceededException(requests.size(), maxBatchSize);
        }
        return mlApiClient.predictBatch(requests.stream().map(this::toPayload).toList(), requestId)
            .map(predictions -> {
                List<ForecastResponse> responses = new ArrayList<>();
                for (int i = 0; i < requests.size(); i++) {
                    responses.add(toResponse(persist(requests.get(i), predictions.get(i), requestId), requestId));
                }
                log.info("Batch forecast saved | count={} | requestId={}", predictions.size(), requestId);
                return responses;
            });
    }

    @Transactional(readOnly = true)
    public Page<ForecastResponse> getHistory(String category, String region, Pageable pageable) {
        return repository.findByCategoryAndRegionOrderByCreatedAtDesc(category, region, pageable)
            .map(r -> toResponse(r, r.getRequestId()));
    }

    @Transactional
    public ForecastResponse recordActualDemand(UUID predictionId, double actualDemand) {
        PredictionRecord record = repository.findById(predictionId)
            .orElseThrow(() -> new PredictionNotFoundException(predictionId));
        record.setActualDemand(actualDemand);
        PredictionRecord saved = repository.save(record);
        log.info("Actual demand recorded | id={} | predicted={} | actual={}", predictionId, record.getPredictedDemand(), actualDemand);
        return toResponse(saved, saved.getRequestId());
    }

    private PredictionRecord persist(ForecastRequest req, MlApiClient.MlPredictResult prediction, String requestId) {
        return repository.save(PredictionRecord.builder()
            .forecastDate(req.getDate()).category(req.getCategory()).region(req.getRegion())
            .inventoryLevel(req.getInventoryLevel()).unitsOrdered(req.getUnitsOrdered())
            .price(req.getPrice()).discount(req.getDiscount())
            .weatherCondition(req.getWeatherCondition()).promotion(req.isPromotion())
            .competitorPricing(req.getCompetitorPricing()).seasonality(req.getSeasonality())
            .epidemic(req.isEpidemic())
            .predictedDemand(prediction.demand())
            .lowerBound(prediction.lowerBound())
            .upperBound(prediction.upperBound())
            .requestId(requestId).build());
    }

    private MlApiClient.MlPredictPayload toPayload(ForecastRequest req) {
        return new MlApiClient.MlPredictPayload(
            req.getDate(), req.getCategory(), req.getRegion(),
            req.getInventoryLevel(), req.getUnitsSold(), req.getUnitsOrdered(),
            req.getPrice(), req.getDiscount(), req.getWeatherCondition(),
            req.isPromotion(), req.getCompetitorPricing(), req.getSeasonality(), req.isEpidemic()
        );
    }

    private ForecastResponse toResponse(PredictionRecord r, String requestId) {
        return ForecastResponse.builder()
            .predictionId(r.getId()).demand(r.getPredictedDemand())
            .lowerBound(r.getLowerBound()).upperBound(r.getUpperBound())
            .forecastDate(r.getForecastDate()).category(r.getCategory()).region(r.getRegion())
            .createdAt(r.getCreatedAt() != null ? r.getCreatedAt() : Instant.now())
            .requestId(requestId).build();
    }
}
