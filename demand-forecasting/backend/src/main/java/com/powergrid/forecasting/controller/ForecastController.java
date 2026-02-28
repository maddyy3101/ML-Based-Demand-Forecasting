package com.powergrid.forecasting.controller;

import com.powergrid.forecasting.dto.forecast.ActualQuantityRequest;
import com.powergrid.forecasting.dto.forecast.AsyncJobResponse;
import com.powergrid.forecasting.dto.forecast.BatchForecastRequest;
import com.powergrid.forecasting.dto.forecast.ExplanationResponse;
import com.powergrid.forecasting.dto.forecast.ForecastHistoryResponse;
import com.powergrid.forecasting.dto.forecast.ForecastRequest;
import com.powergrid.forecasting.dto.forecast.ForecastResponse;
import com.powergrid.forecasting.service.ForecastService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/forecasts")
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @PostMapping({"", "/"})
    public ForecastResponse forecast(@Valid @RequestBody ForecastRequest request, Authentication authentication) {
        return forecastService.forecastDemand(request, authentication.getName());
    }

    @PostMapping("/batch")
    public List<ForecastResponse> batch(@Valid @RequestBody BatchForecastRequest request, Authentication authentication) {
        return forecastService.forecastBatch(request, authentication.getName());
    }

    @GetMapping("/history")
    public Page<ForecastHistoryResponse> history(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String materialType,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String projectPhase
    ) {
        return forecastService.history(pageable, materialType, region, projectPhase);
    }

    @PatchMapping("/{id}/actual")
    public ForecastResponse recordActual(
            @PathVariable UUID id,
            @Valid @RequestBody ActualQuantityRequest request
    ) {
        return forecastService.recordActual(id, request.actualQuantity());
    }

    @GetMapping("/features")
    public Map<String, Object> featureImportance() {
        return forecastService.getFeatureImportance();
    }

    @GetMapping("/accuracy")
    public Map<String, Object> accuracy() {
        return forecastService.getAccuracy();
    }

    @GetMapping("/{id}/explanation")
    public ExplanationResponse explanation(@PathVariable UUID id) {
        return forecastService.getExplanation(id);
    }

    @PostMapping("/async")
    public AsyncJobResponse asyncForecast(@Valid @RequestBody ForecastRequest request, Authentication authentication) {
        String jobId = forecastService.submitAsyncForecast(request, authentication.getName());
        return new AsyncJobResponse(jobId, "PENDING", "Async forecast job submitted");
    }

    @PostMapping("/what-if")
    public Map<String, Object> whatIf(@Valid @RequestBody ForecastRequest request) {
        return forecastService.whatIf(request);
    }
}
