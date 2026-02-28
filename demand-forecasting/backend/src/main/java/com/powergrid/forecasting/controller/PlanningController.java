package com.powergrid.forecasting.controller;

import com.powergrid.forecasting.dto.PlanningExceptionDto;
import com.powergrid.forecasting.dto.PurchasePlanRequestDto;
import com.powergrid.forecasting.dto.PurchasePlanResponseDto;
import com.powergrid.forecasting.dto.ReplenishmentRequestDto;
import com.powergrid.forecasting.dto.ReplenishmentResponseDto;
import com.powergrid.forecasting.service.PlanningService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/planning")
public class PlanningController {

    private final PlanningService planningService;

    public PlanningController(PlanningService planningService) {
        this.planningService = planningService;
    }

    @PostMapping("/replenishment")
    public ReplenishmentResponseDto replenishment(@Valid @RequestBody ReplenishmentRequestDto request) {
        return planningService.computeReplenishment(request);
    }

    @PostMapping("/purchase-plan")
    public PurchasePlanResponseDto purchasePlan(@Valid @RequestBody PurchasePlanRequestDto request) {
        return planningService.generatePurchasePlan(request);
    }

    @GetMapping("/exceptions")
    public List<PlanningExceptionDto> exceptions(Authentication authentication) {
        return planningService.getExceptions(null);
    }
}
