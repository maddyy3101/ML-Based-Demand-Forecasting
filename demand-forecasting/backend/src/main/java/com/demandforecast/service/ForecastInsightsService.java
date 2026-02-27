package com.demandforecast.service;

import com.demandforecast.client.MlApiClient;
import com.demandforecast.dto.BacktestReportResponse;
import com.demandforecast.dto.BacktestRequest;
import com.demandforecast.dto.DriftSummaryResponse;
import com.demandforecast.dto.ExplanationResponse;
import com.demandforecast.dto.ForecastRequest;
import com.demandforecast.dto.WhatIfRequest;
import com.demandforecast.dto.WhatIfResponse;
import com.demandforecast.entity.PredictionRecord;
import com.demandforecast.exception.InvalidBacktestRequestException;
import com.demandforecast.exception.PredictionNotFoundException;
import com.demandforecast.repository.PredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ForecastInsightsService {

    private static final double DRIFT_THRESHOLD = 1.0;

    private final PredictionRepository repository;
    private final MlApiClient mlApiClient;

    public BacktestReportResponse runBacktest(BacktestRequest request) {
        if (request.getFromDate().isAfter(request.getToDate())) {
            throw new InvalidBacktestRequestException("fromDate must be before or equal to toDate");
        }

        List<PredictionRecord> records = repository.findAccuracyRecords(
            request.getCategory(), request.getRegion(), request.getFromDate(), request.getToDate());
        Metrics overall = metrics(records);

        List<BacktestReportResponse.SegmentMetrics> segments = records.stream()
            .collect(Collectors.groupingBy(r -> r.getCategory() + "||" + r.getRegion()))
            .entrySet().stream()
            .map(entry -> {
                String[] parts = entry.getKey().split("\\|\\|");
                Metrics m = metrics(entry.getValue());
                return BacktestReportResponse.SegmentMetrics.builder()
                    .category(parts[0])
                    .region(parts[1])
                    .sampleCount(m.sampleCount())
                    .mae(m.mae())
                    .rmse(m.rmse())
                    .mape(m.mape())
                    .build();
            })
            .sorted(Comparator.comparing(BacktestReportResponse.SegmentMetrics::getCategory)
                .thenComparing(BacktestReportResponse.SegmentMetrics::getRegion))
            .toList();

        return BacktestReportResponse.builder()
            .sampleCount(overall.sampleCount())
            .mae(overall.mae())
            .rmse(overall.rmse())
            .mape(overall.mape())
            .fromDate(request.getFromDate())
            .toDate(request.getToDate())
            .category(request.getCategory())
            .region(request.getRegion())
            .segments(segments)
            .build();
    }

    public DriftSummaryResponse driftSummary(Integer baselineDays, Integer recentDays) {
        int baseWindow = baselineDays != null && baselineDays > 0 ? baselineDays : 30;
        int recWindow = recentDays != null && recentDays > 0 ? recentDays : 7;

        LocalDate recentTo = LocalDate.now();
        LocalDate recentFrom = recentTo.minusDays(recWindow - 1L);
        LocalDate baselineTo = recentFrom.minusDays(1);
        LocalDate baselineFrom = baselineTo.minusDays(baseWindow - 1L);

        List<PredictionRecord> baseline = repository.findByForecastDateBetweenOrderByForecastDateAsc(baselineFrom, baselineTo);
        List<PredictionRecord> recent = repository.findByForecastDateBetweenOrderByForecastDateAsc(recentFrom, recentTo);

        double predDriftScore = driftScore(
            baseline, recent, PredictionRecord::getPredictedDemand);

        List<DriftSummaryResponse.FeatureDrift> featureDrift = featureExtractors().entrySet().stream()
            .map(entry -> {
                String feature = entry.getKey();
                ToDoubleFunction<PredictionRecord> extractor = entry.getValue();
                double baseMean = mean(baseline, extractor);
                double recentMean = mean(recent, extractor);
                double score = driftScore(baseline, recent, extractor);
                return DriftSummaryResponse.FeatureDrift.builder()
                    .feature(feature)
                    .baselineMean(round(baseMean))
                    .recentMean(round(recentMean))
                    .driftScore(round(score))
                    .driftDetected(score >= DRIFT_THRESHOLD)
                    .build();
            })
            .sorted(Comparator.comparing(
                DriftSummaryResponse.FeatureDrift::getDriftScore,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        return DriftSummaryResponse.builder()
            .baselineFrom(baselineFrom)
            .baselineTo(baselineTo)
            .recentFrom(recentFrom)
            .recentTo(recentTo)
            .baselineSampleSize(baseline.size())
            .recentSampleSize(recent.size())
            .predictionDriftScore(round(predDriftScore))
            .predictionDriftDetected(predDriftScore >= DRIFT_THRESHOLD)
            .featureDrift(featureDrift)
            .build();
    }

    public ExplanationResponse explain(UUID predictionId) {
        PredictionRecord record = repository.findById(predictionId)
            .orElseThrow(() -> new PredictionNotFoundException(predictionId));

        List<PredictionRecord> history = repository.findAll();
        Map<String, Double> importances = fetchFeatureImportances();

        List<ExplanationResponse.FeatureContribution> contributions = explanationExtractors().entrySet().stream()
            .map(entry -> {
                String feature = entry.getKey();
                ToDoubleFunction<PredictionRecord> extractor = entry.getValue();
                double value = extractor.applyAsDouble(record);
                double baseline = mean(history, extractor);
                double std = std(history, extractor);
                double importance = importances.getOrDefault(feature, 0.0);
                double normalizedDelta = std > 0 ? (value - baseline) / std : (value - baseline);
                double contribution = importance * normalizedDelta;

                return ExplanationResponse.FeatureContribution.builder()
                    .feature(feature)
                    .value(round(value))
                    .baseline(round(baseline))
                    .importance(round(importance))
                    .contribution(round(contribution))
                    .build();
            })
            .sorted(Comparator.comparing(
                ExplanationResponse.FeatureContribution::getContribution,
                Comparator.nullsLast((a, b) -> Double.compare(Math.abs(b), Math.abs(a)))))
            .limit(12)
            .toList();

        return ExplanationResponse.builder()
            .predictionId(predictionId)
            .demand(record.getPredictedDemand())
            .method("surrogate_importance_weighted_delta")
            .generatedAt(Instant.now())
            .contributions(contributions)
            .build();
    }

    public Mono<WhatIfResponse> runWhatIf(WhatIfRequest request, String requestId) {
        ForecastRequest base = request.getBase();
        return mlApiClient.predict(toPayload(base), requestId)
            .flatMap(basePrediction -> Flux.fromIterable(request.getScenarios())
                .index()
                .concatMap(tuple -> {
                    long idx = tuple.getT1();
                    WhatIfRequest.ScenarioOverride scenario = tuple.getT2();
                    ForecastRequest variant = applyScenario(base, scenario);
                    String name = scenario.getName() != null && !scenario.getName().isBlank()
                        ? scenario.getName() : "scenario-" + (idx + 1);
                    return mlApiClient.predict(toPayload(variant), requestId)
                        .map(pred -> WhatIfResponse.ScenarioResult.builder()
                            .name(name)
                            .demand(pred.demand())
                            .lowerBound(pred.lowerBound())
                            .upperBound(pred.upperBound())
                            .demandDelta(round(pred.demand() - basePrediction.demand()))
                            .build());
                })
                .collectList()
                .map(items -> WhatIfResponse.builder()
                    .baseDemand(basePrediction.demand())
                    .baseLowerBound(basePrediction.lowerBound())
                    .baseUpperBound(basePrediction.upperBound())
                    .scenarios(items)
                    .build()));
    }

    private ForecastRequest applyScenario(ForecastRequest base, WhatIfRequest.ScenarioOverride scenario) {
        return ForecastRequest.builder()
            .date(scenario.getDate() != null ? scenario.getDate() : base.getDate())
            .category(scenario.getCategory() != null ? scenario.getCategory() : base.getCategory())
            .region(scenario.getRegion() != null ? scenario.getRegion() : base.getRegion())
            .inventoryLevel(scenario.getInventoryLevel() != null ? scenario.getInventoryLevel() : base.getInventoryLevel())
            .unitsSold(scenario.getUnitsSold() != null ? scenario.getUnitsSold() : base.getUnitsSold())
            .unitsOrdered(scenario.getUnitsOrdered() != null ? scenario.getUnitsOrdered() : base.getUnitsOrdered())
            .price(scenario.getPrice() != null ? scenario.getPrice() : base.getPrice())
            .discount(scenario.getDiscount() != null ? scenario.getDiscount() : base.getDiscount())
            .weatherCondition(scenario.getWeatherCondition() != null ? scenario.getWeatherCondition() : base.getWeatherCondition())
            .promotion(scenario.getPromotion() != null ? scenario.getPromotion() : base.isPromotion())
            .competitorPricing(scenario.getCompetitorPricing() != null ? scenario.getCompetitorPricing() : base.getCompetitorPricing())
            .seasonality(scenario.getSeasonality() != null ? scenario.getSeasonality() : base.getSeasonality())
            .epidemic(scenario.getEpidemic() != null ? scenario.getEpidemic() : base.isEpidemic())
            .build();
    }

    private MlApiClient.MlPredictPayload toPayload(ForecastRequest req) {
        return new MlApiClient.MlPredictPayload(
            req.getDate(), req.getCategory(), req.getRegion(),
            req.getInventoryLevel(), req.getUnitsSold(), req.getUnitsOrdered(),
            req.getPrice(), req.getDiscount(), req.getWeatherCondition(),
            req.isPromotion(), req.getCompetitorPricing(), req.getSeasonality(), req.isEpidemic()
        );
    }

    private Metrics metrics(List<PredictionRecord> records) {
        if (records.isEmpty()) {
            return new Metrics(0, null, null, null);
        }
        double absError = 0.0;
        double sqError = 0.0;
        double ape = 0.0;
        int apeCount = 0;
        for (PredictionRecord r : records) {
            double actual = r.getActualDemand();
            double predicted = r.getPredictedDemand();
            double err = predicted - actual;
            absError += Math.abs(err);
            sqError += err * err;
            if (actual != 0.0d) {
                ape += Math.abs(err / actual);
                apeCount++;
            }
        }
        double n = records.size();
        Double mape = apeCount > 0 ? (ape / apeCount) * 100.0 : null;
        return new Metrics(
            records.size(),
            round(absError / n),
            round(Math.sqrt(sqError / n)),
            round(mape)
        );
    }

    private double driftScore(List<PredictionRecord> baseline, List<PredictionRecord> recent,
                              ToDoubleFunction<PredictionRecord> extractor) {
        if (baseline.isEmpty() || recent.isEmpty()) {
            return 0.0;
        }
        double baselineMean = mean(baseline, extractor);
        double baselineStd = std(baseline, extractor);
        double recentMean = mean(recent, extractor);
        double denom = baselineStd > 1e-9 ? baselineStd : 1.0;
        return Math.abs(recentMean - baselineMean) / denom;
    }

    private double mean(List<PredictionRecord> records, ToDoubleFunction<PredictionRecord> extractor) {
        if (records.isEmpty()) {
            return 0.0;
        }
        return records.stream().mapToDouble(extractor).average().orElse(0.0);
    }

    private double std(List<PredictionRecord> records, ToDoubleFunction<PredictionRecord> extractor) {
        if (records.size() < 2) {
            return 0.0;
        }
        double mean = mean(records, extractor);
        double variance = records.stream()
            .mapToDouble(r -> {
                double v = extractor.applyAsDouble(r);
                return (v - mean) * (v - mean);
            })
            .average()
            .orElse(0.0);
        return Math.sqrt(variance);
    }

    private Map<String, ToDoubleFunction<PredictionRecord>> featureExtractors() {
        Map<String, ToDoubleFunction<PredictionRecord>> map = new LinkedHashMap<>();
        map.put("inventoryLevel", PredictionRecord::getInventoryLevel);
        map.put("unitsOrdered", PredictionRecord::getUnitsOrdered);
        map.put("price", PredictionRecord::getPrice);
        map.put("discount", PredictionRecord::getDiscount);
        map.put("competitorPricing", PredictionRecord::getCompetitorPricing);
        map.put("promotion", r -> r.isPromotion() ? 1.0 : 0.0);
        map.put("epidemic", r -> r.isEpidemic() ? 1.0 : 0.0);
        return map;
    }

    private Map<String, ToDoubleFunction<PredictionRecord>> explanationExtractors() {
        Map<String, ToDoubleFunction<PredictionRecord>> map = new LinkedHashMap<>();
        map.put("Inventory Level", PredictionRecord::getInventoryLevel);
        map.put("Units Ordered", PredictionRecord::getUnitsOrdered);
        map.put("Price", PredictionRecord::getPrice);
        map.put("Discount", PredictionRecord::getDiscount);
        map.put("Promotion", r -> r.isPromotion() ? 1.0 : 0.0);
        map.put("Competitor Pricing", PredictionRecord::getCompetitorPricing);
        map.put("Epidemic", r -> r.isEpidemic() ? 1.0 : 0.0);
        map.put("year", r -> r.getForecastDate() != null ? r.getForecastDate().getYear() : 0.0);
        map.put("month", r -> r.getForecastDate() != null ? r.getForecastDate().getMonthValue() : 0.0);
        map.put("day", r -> r.getForecastDate() != null ? r.getForecastDate().getDayOfMonth() : 0.0);
        map.put("week", r -> r.getForecastDate() != null
            ? r.getForecastDate().get(WeekFields.ISO.weekOfWeekBasedYear()) : 0.0);
        map.put("dayofweek", r -> r.getForecastDate() != null ? r.getForecastDate().getDayOfWeek().getValue() : 0.0);
        map.put("quarter", r -> r.getForecastDate() != null ? ((r.getForecastDate().getMonthValue() - 1) / 3 + 1) : 0.0);
        return map;
    }

    private Map<String, Double> fetchFeatureImportances() {
        try {
            return mlApiClient.getFeatureImportance().blockOptional().orElse(Map.of());
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Double round(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record Metrics(long sampleCount, Double mae, Double rmse, Double mape) {}
}
