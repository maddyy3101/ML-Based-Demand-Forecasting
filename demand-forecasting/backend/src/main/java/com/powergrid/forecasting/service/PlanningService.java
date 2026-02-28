package com.powergrid.forecasting.service;

import com.powergrid.forecasting.dto.PlanningExceptionDto;
import com.powergrid.forecasting.dto.PurchasePlanRequestDto;
import com.powergrid.forecasting.dto.PurchasePlanResponseDto;
import com.powergrid.forecasting.dto.ReplenishmentRequestDto;
import com.powergrid.forecasting.dto.ReplenishmentResponseDto;
import com.powergrid.forecasting.dto.inventory.ProcurementRecommendationDto;
import com.powergrid.forecasting.entity.MaterialInventory;
import com.powergrid.forecasting.entity.MaterialMovement;
import com.powergrid.forecasting.entity.ProcurementForecast;
import com.powergrid.forecasting.enums.MaterialType;
import com.powergrid.forecasting.enums.MovementType;
import com.powergrid.forecasting.enums.Region;
import com.powergrid.forecasting.exception.ResourceNotFoundException;
import com.powergrid.forecasting.repository.MaterialInventoryRepository;
import com.powergrid.forecasting.repository.MaterialMovementRepository;
import com.powergrid.forecasting.repository.ProcurementForecastRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PlanningService {

    private final MaterialInventoryRepository inventoryRepository;
    private final MaterialMovementRepository movementRepository;
    private final ProcurementForecastRepository forecastRepository;
    private final InventoryService inventoryService;

    public PlanningService(
            MaterialInventoryRepository inventoryRepository,
            MaterialMovementRepository movementRepository,
            ProcurementForecastRepository forecastRepository,
            InventoryService inventoryService
    ) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.forecastRepository = forecastRepository;
        this.inventoryService = inventoryService;
    }

    public ReplenishmentResponseDto computeReplenishment(ReplenishmentRequestDto request) {
        List<ReplenishmentResponseDto.Line> lines = new ArrayList<>();

        for (ReplenishmentRequestDto.Item item : request.items()) {
            MaterialInventory inventory = inventoryRepository.findById(UUID.fromString(item.inventoryId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + item.inventoryId()));

            Instant since = Instant.now().minusSeconds(30L * 24L * 60L * 60L);
            List<MaterialMovement> history = movementRepository.findByInventoryIdAndTimestampAfter(inventory.getId(), since)
                    .stream().filter(m -> m.getMovementType() == MovementType.DEPLOYMENT).toList();

            double avgDaily = history.stream().mapToInt(MaterialMovement::getQuantity).average().orElse(0.0);
            double variance = history.stream().mapToDouble(m -> Math.pow(m.getQuantity() - avgDaily, 2)).average().orElse(0.0);
            double demandVariability = Math.sqrt(variance);

            double serviceLevel = item.serviceLevel() <= 0 ? 0.95 : item.serviceLevel();
            double z = zValue(serviceLevel);
            int leadTime = item.leadTimeDays() <= 0 ? 30 : item.leadTimeDays();

            int safetyStock = (int) Math.ceil(z * demandVariability * Math.sqrt(leadTime));
            int reorderPoint = (int) Math.ceil(avgDaily * leadTime + safetyStock);

            ProcurementForecast latest = forecastRepository
                    .findTopByMaterialTypeAndRegionOrderByCreatedAtDesc(inventory.getMaterialType(), inventory.getRegion())
                    .orElse(null);
            int predictedDemand = latest == null ? 0 : latest.getPredictedQuantity();
            int recommendedOrder = Math.max(0, reorderPoint + predictedDemand - inventory.getCurrentStock());

            lines.add(new ReplenishmentResponseDto.Line(
                    inventory.getId().toString(),
                    inventory.getMaterialType().getDisplayName(),
                    round(demandVariability),
                    safetyStock,
                    reorderPoint,
                    recommendedOrder
            ));
        }

        return new ReplenishmentResponseDto(lines, Instant.now().toString());
    }

    public PurchasePlanResponseDto generatePurchasePlan(PurchasePlanRequestDto request) {
        List<MaterialInventory> inventoryItems;
        if (request.region() == null || request.region().isBlank()) {
            inventoryItems = inventoryRepository.findAll();
        } else {
            inventoryItems = inventoryRepository.findByRegion(Region.fromDisplayName(request.region()));
        }

        List<PurchasePlanResponseDto.Line> lines = new ArrayList<>();
        double totalBudget = 0.0;

        for (MaterialInventory inventory : inventoryItems) {
            ProcurementForecast latest = forecastRepository
                    .findTopByMaterialTypeAndRegionOrderByCreatedAtDesc(inventory.getMaterialType(), inventory.getRegion())
                    .orElse(null);
            int predicted = latest == null ? 0 : latest.getPredictedQuantity();
            int qty = Math.max(0, (int) Math.ceil(predicted * 1.5 - inventory.getCurrentStock()));
            double estimatedCost = qty * inventory.getUnitCostInr();
            totalBudget += estimatedCost;

            String urgency = qty > 0 && inventory.getCurrentStock() < inventory.getReorderThreshold() ? "HIGH"
                    : qty > 0 ? "MEDIUM" : "LOW";

            lines.add(new PurchasePlanResponseDto.Line(
                    inventory.getMaterialType().getDisplayName(),
                    inventory.getRegion().getDisplayName(),
                    qty,
                    inventory.getUnitLabel(),
                    round(estimatedCost),
                    urgency
            ));
        }

        return new PurchasePlanResponseDto(request.planMonth(), lines, round(totalBudget));
    }

    public List<PlanningExceptionDto> getExceptions(String region) {
        List<ProcurementRecommendationDto> recs = inventoryService.getExceptions(region);
        return recs.stream().map(r -> new PlanningExceptionDto(
                r.materialType(),
                r.region(),
                r.stockStatus(),
                r.urgencyLevel(),
                r.urgencyReason()
        )).toList();
    }

    private double zValue(double serviceLevel) {
        if (serviceLevel >= 0.99) {
            return 2.33;
        }
        if (serviceLevel >= 0.98) {
            return 2.05;
        }
        if (serviceLevel >= 0.95) {
            return 1.65;
        }
        if (serviceLevel >= 0.90) {
            return 1.28;
        }
        return 1.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
