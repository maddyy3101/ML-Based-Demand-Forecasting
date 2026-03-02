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
import com.powergrid.forecasting.exception.ValidationFailureException;
import com.powergrid.forecasting.repository.MaterialInventoryRepository;
import com.powergrid.forecasting.repository.MaterialMovementRepository;
import com.powergrid.forecasting.repository.ProcurementForecastRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PlanningService {

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);

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
        String planMonth = request.planMonth() == null ? "" : request.planMonth().trim();
        if (!planMonth.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
            throw new ValidationFailureException("planMonth must be in YYYY-MM format.");
        }

        String regionInput = request.region() == null ? "" : request.region().trim();
        List<MaterialInventory> inventoryItems;
        if (regionInput.isBlank()) {
            inventoryItems = inventoryRepository.findAll();
        } else {
            Region region;
            try {
                region = Region.fromDisplayName(regionInput);
            } catch (IllegalArgumentException ex) {
                throw new ValidationFailureException("Invalid region: " + regionInput);
            }
            inventoryItems = inventoryRepository.findByRegion(region);
        }

        List<PurchasePlanResponseDto.Line> lines = new ArrayList<>();
        double totalBudget = 0.0;

        for (MaterialInventory inventory : inventoryItems) {
            try {
                if (inventory.getMaterialType() == null || inventory.getRegion() == null) {
                    log.warn("Skipping inventory record {} due to missing materialType/region", inventory.getId());
                    continue;
                }

                int predicted = resolvePredictedDemand(inventory);
                int demandBasis = Math.max(predicted, inventory.getReorderThreshold());
                int qty = Math.max(0, (int) Math.ceil(demandBasis * 1.25 - inventory.getCurrentStock()));
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
            } catch (Exception ex) {
                log.error("Failed to generate purchase-plan line for inventory {}: {}", inventory.getId(), ex.getMessage(), ex);
            }
        }

        return new PurchasePlanResponseDto(planMonth, lines, round(totalBudget));
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

    private int resolvePredictedDemand(MaterialInventory inventory) {
        ProcurementForecast latest = forecastRepository
                .findTopByMaterialTypeAndRegionOrderByCreatedAtDesc(inventory.getMaterialType(), inventory.getRegion())
                .orElse(null);
        if (latest != null && latest.getPredictedQuantity() > 0) {
            return latest.getPredictedQuantity();
        }

        Instant since = Instant.now().minusSeconds(30L * 24L * 60L * 60L);
        List<MaterialMovement> recentDeployments = movementRepository
                .findByInventoryIdAndTimestampAfter(inventory.getId(), since)
                .stream()
                .filter(m -> m.getMovementType() == MovementType.DEPLOYMENT)
                .toList();
        if (!recentDeployments.isEmpty()) {
            double avgDaily = recentDeployments.stream().mapToInt(MaterialMovement::getQuantity).average().orElse(0.0);
            return (int) Math.ceil(avgDaily * 30.0);
        }

        return inventory.getReorderThreshold();
    }
}
