package com.powergrid.forecasting.service;

import com.powergrid.forecasting.dto.inventory.InventoryItemDto;
import com.powergrid.forecasting.dto.inventory.MovementDto;
import com.powergrid.forecasting.dto.inventory.MovementRequest;
import com.powergrid.forecasting.dto.inventory.ProcurementRecommendationDto;
import com.powergrid.forecasting.dto.inventory.RecommendationActionRequest;
import com.powergrid.forecasting.dto.inventory.RecommendationActionResponse;
import com.powergrid.forecasting.entity.MaterialInventory;
import com.powergrid.forecasting.entity.MaterialMovement;
import com.powergrid.forecasting.entity.PowerGridUser;
import com.powergrid.forecasting.entity.ProcurementForecast;
import com.powergrid.forecasting.entity.RecommendationAction;
import com.powergrid.forecasting.enums.MaterialType;
import com.powergrid.forecasting.enums.MovementType;
import com.powergrid.forecasting.enums.RecommendationActionType;
import com.powergrid.forecasting.enums.Region;
import com.powergrid.forecasting.enums.StockStatus;
import com.powergrid.forecasting.exception.ResourceNotFoundException;
import com.powergrid.forecasting.repository.MaterialInventoryRepository;
import com.powergrid.forecasting.repository.MaterialMovementRepository;
import com.powergrid.forecasting.repository.ProcurementForecastRepository;
import com.powergrid.forecasting.repository.RecommendationActionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final MaterialInventoryRepository inventoryRepository;
    private final MaterialMovementRepository movementRepository;
    private final ProcurementForecastRepository forecastRepository;
    private final RecommendationActionRepository recommendationActionRepository;

    public InventoryService(
            MaterialInventoryRepository inventoryRepository,
            MaterialMovementRepository movementRepository,
            ProcurementForecastRepository forecastRepository,
            RecommendationActionRepository recommendationActionRepository
    ) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.forecastRepository = forecastRepository;
        this.recommendationActionRepository = recommendationActionRepository;
    }

    public List<InventoryItemDto> getInventoryItems(String assignedRegion, boolean isAdmin) {
        List<MaterialInventory> items;
        if (isAdmin || assignedRegion == null || assignedRegion.isBlank()) {
            items = inventoryRepository.findAll();
        } else {
            items = inventoryRepository.findByRegion(Region.fromDisplayName(assignedRegion));
        }
        return items.stream().map(this::toInventoryDto).toList();
    }

    @Transactional
    public MovementDto logMovement(MovementRequest request, PowerGridUser performedBy) {
        MaterialInventory inventory = inventoryRepository.findById(UUID.fromString(request.inventoryId()))
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + request.inventoryId()));

        MovementType movementType = MovementType.valueOf(request.movementType().toUpperCase());
        int quantity = request.quantity();

        if (movementType == MovementType.DEPLOYMENT) {
            int next = inventory.getCurrentStock() - quantity;
            if (next < 0) {
                throw new IllegalStateException(
                        "Cannot deploy " + quantity + " units of " + inventory.getMaterialName()
                                + ". Only " + inventory.getCurrentStock() + " available."
                );
            }
            inventory.setCurrentStock(next);
        } else {
            inventory.setCurrentStock(inventory.getCurrentStock() + quantity);
        }

        inventoryRepository.save(inventory);

        MaterialMovement movement = new MaterialMovement();
        movement.setInventory(inventory);
        movement.setMovementType(movementType);
        movement.setQuantity(quantity);
        movement.setReason(request.reason());
        movement.setProjectId(request.projectId());
        movement.setVendorName(request.vendorName());
        movement.setInvoiceNumber(request.invoiceNumber());
        movement.setNotes(request.notes());
        movement.setPerformedBy(performedBy);

        MaterialMovement saved = movementRepository.save(movement);
        return toMovementDto(saved);
    }

    public List<MovementDto> getMovements(String assignedRegion, boolean isAdmin) {
        List<MaterialMovement> movements;
        if (isAdmin || assignedRegion == null || assignedRegion.isBlank()) {
            movements = movementRepository.findAll();
        } else {
            Region region = Region.fromDisplayName(assignedRegion);
            List<MaterialInventory> inventories = inventoryRepository.findByRegion(region);
            List<UUID> ids = inventories.stream().map(MaterialInventory::getId).toList();
            movements = movementRepository.findAll().stream()
                    .filter(m -> ids.contains(m.getInventory().getId()))
                    .toList();
        }
        return movements.stream().sorted(Comparator.comparing(MaterialMovement::getTimestamp).reversed()).map(this::toMovementDto).toList();
    }

    public List<ProcurementRecommendationDto> getRecommendations(String assignedRegion) {
        return getRecommendations(assignedRegion, null);
    }

    public List<ProcurementRecommendationDto> getRecommendations(String assignedRegion, String username) {
        List<MaterialInventory> inventories;
        if (assignedRegion == null || assignedRegion.isBlank()) {
            inventories = inventoryRepository.findAll();
        } else {
            inventories = inventoryRepository.findByRegion(Region.fromDisplayName(assignedRegion));
        }

        Map<UUID, RecommendationActionType> latestActionsByInventory = new HashMap<>();
        if (username != null && !username.isBlank() && !inventories.isEmpty()) {
            List<UUID> inventoryIds = inventories.stream().map(MaterialInventory::getId).toList();
            List<RecommendationAction> actions = recommendationActionRepository
                    .findByInventoryIdInAndActedByOrderByCreatedAtDesc(inventoryIds, username);
            for (RecommendationAction action : actions) {
                latestActionsByInventory.putIfAbsent(action.getInventory().getId(), action.getActionType());
            }
        }

        List<ProcurementRecommendationDto> recommendations = new ArrayList<>();
        Instant last7Days = Instant.now().minusSeconds(7L * 24L * 60L * 60L);

        for (MaterialInventory inventory : inventories) {
            if (latestActionsByInventory.get(inventory.getId()) == RecommendationActionType.DISMISS) {
                continue;
            }
            List<MaterialMovement> recentMovements = movementRepository.findByInventoryIdAndTimestampAfter(inventory.getId(), last7Days);
            int total7d = recentMovements.stream()
                    .filter(m -> m.getMovementType() == MovementType.DEPLOYMENT)
                    .mapToInt(MaterialMovement::getQuantity)
                    .sum();

            double avgDailyDeployment = total7d / 7.0;
            double daysUntilStockout = inventory.getCurrentStock() / Math.max(avgDailyDeployment, 1.0);

            ProcurementForecast latestForecast = forecastRepository
                    .findTopByMaterialTypeAndRegionOrderByCreatedAtDesc(inventory.getMaterialType(), inventory.getRegion())
                    .orElse(null);

            int predictedDemand = latestForecast == null ? 0 : latestForecast.getPredictedQuantity();
            int recommendedOrderQty = Math.max(0, (int) Math.round(predictedDemand * 1.5 - inventory.getCurrentStock()));

            String urgencyLevel;
            String urgencyReason;
            if (daysUntilStockout < 3 || inventory.getCurrentStock() < inventory.getReorderThreshold() * 0.5) {
                urgencyLevel = "HIGH";
                urgencyReason = "Immediate replenishment required to avoid stock-out";
            } else if (daysUntilStockout < 7 || inventory.getCurrentStock() < inventory.getReorderThreshold()) {
                urgencyLevel = "MEDIUM";
                urgencyReason = "Approaching reorder threshold";
            } else {
                urgencyLevel = "LOW";
                urgencyReason = "Stock level stable";
            }

            StockStatus status;
            if (inventory.getCurrentStock() < inventory.getReorderThreshold() * 0.3) {
                status = StockStatus.CRITICAL;
            } else if (inventory.getCurrentStock() < inventory.getReorderThreshold()) {
                status = StockStatus.LOW;
            } else if (predictedDemand > 0 && inventory.getCurrentStock() > predictedDemand * 3) {
                status = StockStatus.OVERSTOCK;
            } else {
                status = StockStatus.OK;
            }

            recommendations.add(
                    new ProcurementRecommendationDto(
                            inventory.getId().toString(),
                            inventory.getMaterialType().getDisplayName(),
                            inventory.getMaterialName(),
                            inventory.getUnitLabel(),
                            inventory.getRegion().getDisplayName(),
                            inventory.getTowerType().getDisplayName(),
                            inventory.getCurrentStock(),
                            inventory.getReorderThreshold(),
                            status.name(),
                            predictedDemand,
                            avgDailyDeployment,
                            daysUntilStockout,
                            recommendedOrderQty,
                            urgencyLevel,
                            urgencyReason
                    )
            );
        }

        recommendations.sort(
                Comparator.comparingInt((ProcurementRecommendationDto r) -> stockPriority(r.stockStatus()))
                        .thenComparingInt(r -> urgencyPriority(r.urgencyLevel()))
        );

        return recommendations;
    }

    @Transactional
    public RecommendationActionResponse raiseRecommendation(String inventoryId, RecommendationActionRequest request, PowerGridUser user) {
        MaterialInventory inventory = inventoryRepository.findById(UUID.fromString(inventoryId))
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + inventoryId));

        int recommendedQty = request.recommendedOrderQty() == null
                ? suggestedOrderQty(inventory)
                : Math.max(0, request.recommendedOrderQty());

        RecommendationAction action = new RecommendationAction();
        action.setInventory(inventory);
        action.setActionType(RecommendationActionType.RAISE_REQUEST);
        action.setRecommendedOrderQty(recommendedQty);
        action.setNote(trimToNull(request.note()));
        action.setActedBy(user.getUsername());
        RecommendationAction saved = recommendationActionRepository.save(action);
        return toRecommendationAction(saved);
    }

    @Transactional
    public RecommendationActionResponse dismissRecommendation(String inventoryId, RecommendationActionRequest request, PowerGridUser user) {
        MaterialInventory inventory = inventoryRepository.findById(UUID.fromString(inventoryId))
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + inventoryId));

        RecommendationAction action = new RecommendationAction();
        action.setInventory(inventory);
        action.setActionType(RecommendationActionType.DISMISS);
        action.setRecommendedOrderQty(0);
        action.setNote(trimToNull(request.note()));
        action.setActedBy(user.getUsername());
        RecommendationAction saved = recommendationActionRepository.save(action);
        return toRecommendationAction(saved);
    }

    public List<RecommendationActionResponse> getRecommendationActions(String username, boolean includeAll) {
        List<RecommendationAction> rows = includeAll
                ? recommendationActionRepository.findTop100ByOrderByCreatedAtDesc()
                : recommendationActionRepository.findTop100ByActedByOrderByCreatedAtDesc(username);
        return rows.stream().map(this::toRecommendationAction).toList();
    }

    public List<ProcurementRecommendationDto> getExceptions(String assignedRegion) {
        return getRecommendations(assignedRegion).stream()
                .filter(r -> !"OK".equals(r.stockStatus()) || "HIGH".equals(r.urgencyLevel()))
                .toList();
    }

    private int stockPriority(String status) {
        return switch (status) {
            case "CRITICAL" -> 0;
            case "LOW" -> 1;
            case "OVERSTOCK" -> 2;
            default -> 3;
        };
    }

    private int urgencyPriority(String urgency) {
        return switch (urgency) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private int suggestedOrderQty(MaterialInventory inventory) {
        ProcurementForecast latest = forecastRepository
                .findTopByMaterialTypeAndRegionOrderByCreatedAtDesc(inventory.getMaterialType(), inventory.getRegion())
                .orElse(null);
        int predicted = latest == null ? 0 : latest.getPredictedQuantity();
        return Math.max(0, (int) Math.ceil(predicted * 1.5 - inventory.getCurrentStock()));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private InventoryItemDto toInventoryDto(MaterialInventory item) {
        StockStatus status;
        if (item.getCurrentStock() < item.getReorderThreshold() * 0.3) {
            status = StockStatus.CRITICAL;
        } else if (item.getCurrentStock() < item.getReorderThreshold()) {
            status = StockStatus.LOW;
        } else if (item.getCurrentStock() > item.getMaxCapacity() * 0.85) {
            status = StockStatus.OVERSTOCK;
        } else {
            status = StockStatus.OK;
        }

        return new InventoryItemDto(
                item.getId().toString(),
                item.getMaterialType().name(),
                item.getMaterialName(),
                item.getUnitLabel(),
                item.getSku(),
                item.getRegion().getDisplayName(),
                item.getTowerType().getDisplayName(),
                item.getCurrentStock(),
                item.getReorderThreshold(),
                item.getMaxCapacity(),
                status.name(),
                item.getUnitCostInr(),
                item.getWarehouseLocation(),
                item.getLastUpdated()
        );
    }

    private MovementDto toMovementDto(MaterialMovement movement) {
        return new MovementDto(
                movement.getId().toString(),
                movement.getInventory().getMaterialName(),
                movement.getMovementType().name(),
                movement.getQuantity(),
                movement.getReason(),
                movement.getProjectId(),
                movement.getVendorName(),
                movement.getPerformedBy() == null ? null : movement.getPerformedBy().getUsername(),
                movement.getTimestamp()
        );
    }

    private RecommendationActionResponse toRecommendationAction(RecommendationAction action) {
        return new RecommendationActionResponse(
                action.getId().toString(),
                action.getInventory().getId().toString(),
                action.getActionType().name(),
                action.getRecommendedOrderQty(),
                action.getNote(),
                action.getActedBy(),
                action.getCreatedAt()
        );
    }
}
