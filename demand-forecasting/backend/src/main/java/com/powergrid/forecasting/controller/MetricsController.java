package com.powergrid.forecasting.controller;

import com.powergrid.forecasting.service.MetricsService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/performance")
    public Map<String, Object> performance(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String materialType,
            @RequestParam(required = false) String region
    ) {
        return metricsService.performance(from, to, materialType, region);
    }

    @GetMapping("/drift")
    public Map<String, Object> drift() {
        return metricsService.driftSummary();
    }
}
