package com.demandforecast.controller;

import com.demandforecast.dto.InventoryExceptionResponse;
import com.demandforecast.dto.PurchasePlanRequest;
import com.demandforecast.dto.PurchasePlanResponse;
import com.demandforecast.dto.ReplenishmentPlanRequest;
import com.demandforecast.dto.ReplenishmentPlanResponse;
import com.demandforecast.service.SupplyChainPlanningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/v1/planning")
@RequiredArgsConstructor
public class PlanningController {

    private final SupplyChainPlanningService planningService;

    @PostMapping("/replenishment")
    public ResponseEntity<ReplenishmentPlanResponse> replenishment(
            @Valid @RequestBody ReplenishmentPlanRequest request) {
        return ResponseEntity.ok(planningService.replenishmentPlan(request));
    }

    @PostMapping("/purchase-plan")
    public ResponseEntity<PurchasePlanResponse> purchasePlan(
            @Valid @RequestBody PurchasePlanRequest request) {
        return ResponseEntity.ok(planningService.purchasePlan(request));
    }

    @GetMapping("/exceptions")
    public ResponseEntity<InventoryExceptionResponse> exceptions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "7") @Min(1) @Max(365) int stockoutCoverageDays,
            @RequestParam(defaultValue = "45") @Min(1) @Max(730) int overstockCoverageDays,
            @RequestParam(defaultValue = "200") @Min(1) @Max(10000) int limit) {
        return ResponseEntity.ok(planningService.detectExceptions(
            fromDate, toDate, stockoutCoverageDays, overstockCoverageDays, limit));
    }
}
