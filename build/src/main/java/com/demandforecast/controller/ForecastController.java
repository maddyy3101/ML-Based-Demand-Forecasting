package com.demandforecast.controller;

import com.demandforecast.client.MlApiClient;
import com.demandforecast.dto.ForecastRequest;
import com.demandforecast.dto.ForecastResponse;
import com.demandforecast.service.ForecastService;
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
import reactor.core.publisher.Mono;

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

    @PostMapping("/forecasts")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<ForecastResponse>> forecast(
            @Valid @RequestBody ForecastRequest request,
            HttpServletRequest httpRequest) {

        String requestId = resolveRequestId(httpRequest);
        log.info("POST /forecasts | category={} | region={} | date={} | requestId={}",
                 request.getCategory(), request.getRegion(), request.getDate(), requestId);

        return forecastService.forecast(request, requestId)
            .map(response -> ResponseEntity
                .status(HttpStatus.CREATED)
                .header("X-Request-ID", requestId)
                .body(response));
    }

    @PostMapping("/forecasts/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<List<ForecastResponse>>> forecastBatch(
            @Valid @RequestBody List<@Valid ForecastRequest> requests,
            HttpServletRequest httpRequest) {

        String requestId = resolveRequestId(httpRequest);
        log.info("POST /forecasts/batch | count={} | requestId={}", requests.size(), requestId);

        return forecastService.forecastBatch(requests, requestId)
            .map(responses -> ResponseEntity
                .status(HttpStatus.CREATED)
                .header("X-Request-ID", requestId)
                .body(responses));
    }

    @GetMapping("/forecasts/history")
    public ResponseEntity<Page<ForecastResponse>> history(
            @RequestParam String category,
            @RequestParam String region,
            @RequestParam(defaultValue = "0")  @Min(0)       int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(
            forecastService.getHistory(category, region, pageable));
    }

    @PatchMapping("/forecasts/{id}/actual")
    public ResponseEntity<ForecastResponse> recordActual(
            @PathVariable UUID id,
            @RequestParam @Min(0) double actualDemand) {

        log.info("PATCH /forecasts/{}/actual | actualDemand={}", id, actualDemand);
        return ResponseEntity.ok(
            forecastService.recordActualDemand(id, actualDemand));
    }

    @GetMapping("/forecasts/features")
    public Mono<ResponseEntity<Map<String, Double>>> featureImportance() {
        return mlApiClient.getFeatureImportance()
            .map(ResponseEntity::ok);
    }

    @GetMapping("/ml/health")
    public Mono<ResponseEntity<Map<String, Object>>> mlHealth() {
        return mlApiClient.isHealthy()
            .map(healthy -> {
                Map<String, Object> body = Map.of(
                    "mlApi", healthy ? "UP" : "DOWN",
                    "status", healthy ? "ok" : "degraded"
                );
                HttpStatus status = healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
                return ResponseEntity.status(status).body(body);
            });
    }

    private String resolveRequestId(HttpServletRequest request) {
        String id = request.getHeader("X-Request-ID");
        return (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
    }
}
