package com.powergrid.forecasting.controller;

import com.powergrid.forecasting.dto.BacktestRequest;
import com.powergrid.forecasting.dto.JobStatusDto;
import com.powergrid.forecasting.service.BacktestService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/backtests")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @PostMapping({"", "/"})
    public Map<String, Object> submit(@Valid @RequestBody BacktestRequest request, Authentication authentication) {
        String jobId = backtestService.submit(request, authentication.getName());
        return Map.of("jobId", jobId, "status", "PENDING", "message", "Backtest job submitted");
    }

    @GetMapping("/{jobId}")
    public JobStatusDto get(@PathVariable String jobId) {
        return backtestService.get(jobId);
    }
}
