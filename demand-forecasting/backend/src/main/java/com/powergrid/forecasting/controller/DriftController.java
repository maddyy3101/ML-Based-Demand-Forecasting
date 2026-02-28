package com.powergrid.forecasting.controller;

import com.powergrid.forecasting.service.MetricsService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drift")
public class DriftController {

    private final MetricsService metricsService;

    public DriftController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return metricsService.driftSummary();
    }
}
