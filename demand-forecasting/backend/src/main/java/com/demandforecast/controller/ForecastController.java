package com.demandforecast.controller;

import com.demandforecast.client.MlApiClient;
import com.demandforecast.dto.ActivateModelRequest;
import com.demandforecast.dto.AccuracyMetricsResponse;
import com.demandforecast.dto.AsyncJobResponse;
import com.demandforecast.dto.BacktestRequest;
import com.demandforecast.dto.DriftSummaryResponse;
import com.demandforecast.dto.ExplanationResponse;
import com.demandforecast.dto.ForecastRequest;
import com.demandforecast.dto.ForecastResponse;
import com.demandforecast.dto.ModelVersionResponse;
import com.demandforecast.dto.WhatIfRequest;
import com.demandforecast.dto.WhatIfResponse;
import com.demandforecast.service.AsyncJobService;
import com.demandforecast.service.ForecastService;
import com.demandforecast.service.ForecastInsightsService;
import com.demandforecast.service.ModelRegistryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastService forecastService;
    private final MlApiClient     mlApiClient;
    private final AsyncJobService asyncJobService;
    private final ForecastInsightsService insightsService;
    private final ModelRegistryService modelRegistryService;

    @PostMapping("/forecasts")
    public Mono<ResponseEntity<ForecastResponse>> forecast(
            @Valid @RequestBody ForecastRequest request, HttpServletRequest httpRequest) {
        String requestId = resolveRequestId(httpRequest);
        log.info("POST /forecasts | category={} | region={} | date={} | requestId={}",
                 request.getCategory(), request.getRegion(), request.getDate(), requestId);
        return forecastService.forecast(request, requestId)
            .map(r -> ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Request-ID", requestId).body(r));
    }

    @PostMapping("/forecasts/batch")
    public Mono<ResponseEntity<List<ForecastResponse>>> forecastBatch(
            @Valid @RequestBody List<@Valid ForecastRequest> requests, HttpServletRequest httpRequest) {
        String requestId = resolveRequestId(httpRequest);
        log.info("POST /forecasts/batch | count={} | requestId={}", requests.size(), requestId);
        return forecastService.forecastBatch(requests, requestId)
            .map(r -> ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Request-ID", requestId).body(r));
    }

    @PostMapping("/forecasts/async")
    public ResponseEntity<AsyncJobResponse> forecastAsync(
            @Valid @RequestBody List<@Valid ForecastRequest> requests, HttpServletRequest httpRequest) {
        String requestId = resolveRequestId(httpRequest);
        UUID jobId = asyncJobService.submit(
            "FORECAST_BATCH",
            requestId,
            () -> forecastService.forecastBatchBlocking(requests, requestId)
        );
        return ResponseEntity.accepted()
            .header("X-Request-ID", requestId)
            .header("Location", "/api/v1/jobs/" + jobId)
            .body(asyncJobService.getJob(jobId));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<AsyncJobResponse> jobStatus(@PathVariable UUID jobId) {
        return ResponseEntity.ok(asyncJobService.getJob(jobId));
    }

    @GetMapping("/forecasts/history")
    public ResponseEntity<Page<ForecastResponse>> history(
            @RequestParam String category, @RequestParam String region,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(forecastService.getHistory(category, region,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @PatchMapping("/forecasts/{id}/actual")
    public ResponseEntity<ForecastResponse> recordActual(
            @PathVariable UUID id, @RequestParam @Min(0) double actualDemand) {
        log.info("PATCH /forecasts/{}/actual | actualDemand={}", id, actualDemand);
        return ResponseEntity.ok(forecastService.recordActualDemand(id, actualDemand));
    }

    @GetMapping("/forecasts/features")
    public Mono<ResponseEntity<Map<String, Double>>> featureImportance() {
        return mlApiClient.getFeatureImportance().map(ResponseEntity::ok);
    }

    @GetMapping("/forecasts/accuracy")
    public ResponseEntity<AccuracyMetricsResponse> accuracy(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(forecastService.getAccuracyMetrics(category, region, fromDate, toDate));
    }

    @GetMapping("/metrics/performance")
    public ResponseEntity<AccuracyMetricsResponse> performanceMetrics(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String region,
            @RequestParam(required = false, name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false, name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(forecastService.getAccuracyMetrics(category, region, fromDate, toDate));
    }

    @PostMapping("/backtests")
    public ResponseEntity<AsyncJobResponse> runBacktest(
            @Valid @RequestBody BacktestRequest request, HttpServletRequest httpRequest) {
        String requestId = resolveRequestId(httpRequest);
        UUID jobId = asyncJobService.submit(
            "BACKTEST",
            requestId,
            () -> insightsService.runBacktest(request)
        );
        return ResponseEntity.accepted()
            .header("X-Request-ID", requestId)
            .header("Location", "/api/v1/backtests/" + jobId)
            .body(asyncJobService.getJob(jobId));
    }

    @GetMapping("/backtests/{jobId}")
    public ResponseEntity<AsyncJobResponse> getBacktest(@PathVariable UUID jobId) {
        return ResponseEntity.ok(asyncJobService.getJob(jobId));
    }

    @GetMapping("/drift/summary")
    public ResponseEntity<DriftSummaryResponse> driftSummary(
            @RequestParam(required = false) Integer baselineDays,
            @RequestParam(required = false) Integer recentDays) {
        return ResponseEntity.ok(insightsService.driftSummary(baselineDays, recentDays));
    }

    @GetMapping("/forecasts/{id}/explanation")
    public ResponseEntity<ExplanationResponse> explainForecast(@PathVariable UUID id) {
        return ResponseEntity.ok(insightsService.explain(id));
    }

    @PostMapping("/forecasts/what-if")
    public Mono<ResponseEntity<WhatIfResponse>> whatIf(
            @Valid @RequestBody WhatIfRequest request, HttpServletRequest httpRequest) {
        String requestId = resolveRequestId(httpRequest);
        return insightsService.runWhatIf(request, requestId)
            .map(resp -> ResponseEntity.ok()
                .header("X-Request-ID", requestId)
                .body(resp));
    }

    @GetMapping("/ml/health")
    public Mono<ResponseEntity<Map<String, Object>>> mlHealth() {
        return mlApiClient.isHealthy().map(healthy -> {
            Map<String, Object> body = Map.of("mlApi", healthy ? "UP" : "DOWN",
                                               "status", healthy ? "ok" : "degraded");
            return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
        });
    }

    @GetMapping("/ml/model-info")
    public Mono<ResponseEntity<Map<String, Object>>> modelInfo() {
        return mlApiClient.getModelInfo().map(ResponseEntity::ok);
    }

    @GetMapping("/models/active")
    public ResponseEntity<ModelVersionResponse> activeModel() {
        return ResponseEntity.ok(modelRegistryService.getActiveModel());
    }

    @PostMapping("/models/{version}/activate")
    public ResponseEntity<ModelVersionResponse> activateModel(
            @PathVariable String version,
            @RequestBody(required = false) ActivateModelRequest request,
            HttpServletRequest httpRequest) {
        String requestId = resolveRequestId(httpRequest);
        return ResponseEntity.ok(modelRegistryService.activate(version, request, requestId));
    }

    private String resolveRequestId(HttpServletRequest request) {
        String id = request.getHeader("X-Request-ID");
        return (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
    }
}
