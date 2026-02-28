package com.powergrid.forecasting.controller;

import com.powergrid.forecasting.dto.JobStatusDto;
import com.powergrid.forecasting.dto.JobSummaryDto;
import com.powergrid.forecasting.service.AsyncJobService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final AsyncJobService asyncJobService;

    public JobController(AsyncJobService asyncJobService) {
        this.asyncJobService = asyncJobService;
    }

    @GetMapping
    public List<JobSummaryDto> list(Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return asyncJobService.list(authentication.getName(), isAdmin);
    }

    @GetMapping("/{jobId}")
    public JobStatusDto getJob(@PathVariable String jobId) {
        return asyncJobService.get(jobId);
    }
}
