package com.demandforecast.service;

import com.demandforecast.client.MlApiClient;
import com.demandforecast.dto.AccuracyMetricsResponse;
import com.demandforecast.dto.ForecastRequest;
import com.demandforecast.dto.ForecastResponse;
import com.demandforecast.entity.PredictionRecord;
import com.demandforecast.exception.BatchSizeExceededException;
import com.demandforecast.exception.MlApiException;
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
import java.time.LocalDate;
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

    @Transactional(readOnly = true)
    public AccuracyMetricsResponse getAccuracyMetrics(
            String category, String region, LocalDate fromDate, LocalDate toDate) {
        List<PredictionRecord> records = repository.findAccuracyRecords(category, region, fromDate, toDate);
        if (records.isEmpty()) {
            return AccuracyMetricsResponse.builder()
                .sampleCount(0)
                .fromDate(fromDate)
                .toDate(toDate)
                .category(category)
                .region(region)
                .build();
        }

        double absErrorSum = 0.0;
        double squaredErrorSum = 0.0;
        double apeSum = 0.0;
        int apeCount = 0;

        for (PredictionRecord record : records) {
            double actual = record.getActualDemand();
            double predicted = record.getPredictedDemand();
            double error = predicted - actual;
            absErrorSum += Math.abs(error);
            squaredErrorSum += error * error;
            if (actual != 0.0d) {
                apeSum += Math.abs(error / actual);
                apeCount++;
            }
        }

        double n = records.size();
        Double mape = apeCount > 0 ? (apeSum / apeCount) * 100.0 : null;
        return AccuracyMetricsResponse.builder()
            .sampleCount(records.size())
            .mae(absErrorSum / n)
            .rmse(Math.sqrt(squaredErrorSum / n))
            .mape(mape)
            .fromDate(fromDate)
            .toDate(toDate)
            .category(category)
            .region(region)
            .build();
    }

    @Transactional
    public ForecastResponse forecastBlocking(ForecastRequest req, String requestId) {
        MlApiClient.MlPredictResult prediction = mlApiClient.predict(toPayload(req), requestId)
            .blockOptional()
            .orElseThrow(() -> new MlApiException("ML API returned an empty prediction response"));
        PredictionRecord saved = persist(req, prediction, requestId);
        log.info("Forecast saved (blocking) | id={} | demand={} | requestId={}",
            saved.getId(), prediction.demand(), requestId);
        return toResponse(saved, requestId);
    }

    @Transactional
    public List<ForecastResponse> forecastBatchBlocking(List<ForecastRequest> requests, String requestId) {
        if (requests.size() > maxBatchSize) {
            throw new BatchSizeExceededException(requests.size(), maxBatchSize);
        }
        List<MlApiClient.MlPredictResult> predictions = mlApiClient.predictBatch(
            requests.stream().map(this::toPayload).toList(), requestId)
            .blockOptional()
            .orElseThrow(() -> new MlApiException("ML API returned an empty batch prediction response"));

        if (predictions.size() != requests.size()) {
            throw new MlApiException("ML API batch response size mismatch");
        }

        List<ForecastResponse> responses = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            responses.add(toResponse(persist(requests.get(i), predictions.get(i), requestId), requestId));
        }
        log.info("Batch forecast saved (blocking) | count={} | requestId={}", predictions.size(), requestId);
        return responses;
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
