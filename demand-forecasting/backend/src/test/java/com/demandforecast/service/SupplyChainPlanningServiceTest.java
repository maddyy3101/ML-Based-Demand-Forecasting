package com.demandforecast.service;

import com.demandforecast.dto.InventoryExceptionResponse;
import com.demandforecast.dto.PlanningItemRequest;
import com.demandforecast.dto.PurchasePlanRequest;
import com.demandforecast.dto.ReplenishmentPlanRequest;
import com.demandforecast.dto.StockExceptionType;
import com.demandforecast.entity.PredictionRecord;
import com.demandforecast.repository.PredictionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplyChainPlanningServiceTest {

    @Mock
    PredictionRepository predictionRepository;

    @InjectMocks
    SupplyChainPlanningService planningService;

    private PlanningItemRequest sampleItem() {
        return PlanningItemRequest.builder()
            .productId("SKU-1001")
            .warehouseId("WH-NORTH")
            .currentInventory(120)
            .onOrderInventory(10)
            .leadTimeDays(5)
            .reviewPeriodDays(7)
            .serviceLevel(0.95)
            .minOrderQuantity(20.0)
            .orderMultiple(10.0)
            .demandHistory(List.of(18.0, 19.0, 20.0, 22.0, 17.0, 21.0, 20.0))
            .build();
    }

    @Test
    void replenishmentPlan_returnsInventoryControlMetrics() {
        var request = ReplenishmentPlanRequest.builder()
            .items(List.of(sampleItem()))
            .overstockFactor(1.8)
            .build();

        var response = planningService.replenishmentPlan(request);

        assertThat(response.getItemCount()).isEqualTo(1);
        var item = response.getItems().get(0);
        assertThat(item.getSafetyStock()).isGreaterThan(0.0);
        assertThat(item.getReorderPoint()).isGreaterThan(0.0);
        assertThat(item.getTargetStockLevel()).isGreaterThan(item.getReorderPoint());
        assertThat(item.getRecommendedOrderQuantity()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void purchasePlan_generatesOrderSchedule() {
        var request = PurchasePlanRequest.builder()
            .items(List.of(sampleItem()))
            .horizonDays(30)
            .maxOrdersPerItem(6)
            .overstockFactor(1.8)
            .build();

        var response = planningService.purchasePlan(request);

        assertThat(response.getItemCount()).isEqualTo(1);
        var item = response.getItems().get(0);
        assertThat(item.getTotalForecastDemand()).isGreaterThan(0.0);
        assertThat(item.getServiceLevelEstimate()).isBetween(0.0, 1.0);
        assertThat(item.getOrders()).isNotNull();
    }

    @Test
    void detectExceptions_returnsStockoutAndOverstockRisks() {
        PredictionRecord stockout = PredictionRecord.builder()
            .id(UUID.randomUUID())
            .forecastDate(LocalDate.of(2026, 2, 1))
            .category("Electronics")
            .region("North")
            .inventoryLevel(30)
            .unitsOrdered(10)
            .predictedDemand(25)
            .upperBound(35.0)
            .lowerBound(18.0)
            .build();

        PredictionRecord overstock = PredictionRecord.builder()
            .id(UUID.randomUUID())
            .forecastDate(LocalDate.of(2026, 2, 2))
            .category("Furniture")
            .region("West")
            .inventoryLevel(2000)
            .unitsOrdered(500)
            .predictedDemand(20)
            .upperBound(25.0)
            .lowerBound(15.0)
            .build();

        when(predictionRepository.findByForecastDateBetweenOrderByForecastDateAsc(any(), any()))
            .thenReturn(List.of(stockout, overstock));

        InventoryExceptionResponse response = planningService.detectExceptions(
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 10), 7, 45, 50);

        assertThat(response.getExceptionCount()).isGreaterThanOrEqualTo(2);
        assertThat(response.getExceptions().stream().anyMatch(e -> e.getType() == StockExceptionType.STOCKOUT_RISK)).isTrue();
        assertThat(response.getExceptions().stream().anyMatch(e -> e.getType() == StockExceptionType.OVERSTOCK_RISK)).isTrue();
    }
}
