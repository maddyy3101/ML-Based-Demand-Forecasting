package com.demandforecast.service;

import com.demandforecast.dto.InventoryExceptionResponse;
import com.demandforecast.dto.PlanningItemRequest;
import com.demandforecast.dto.PurchasePlanRequest;
import com.demandforecast.dto.PurchasePlanResponse;
import com.demandforecast.dto.ReplenishmentPlanRequest;
import com.demandforecast.dto.ReplenishmentPlanResponse;
import com.demandforecast.dto.RiskLevel;
import com.demandforecast.dto.StockExceptionType;
import com.demandforecast.entity.PredictionRecord;
import com.demandforecast.exception.PlanningInputException;
import com.demandforecast.repository.PredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupplyChainPlanningService {

    private static final double EPS = 1e-9;

    private final PredictionRepository predictionRepository;

    public ReplenishmentPlanResponse replenishmentPlan(ReplenishmentPlanRequest request) {
        List<ReplenishmentPlanResponse.ItemPlan> plans = request.getItems().stream()
            .map(item -> buildReplenishmentItem(item, request.getOverstockFactor()))
            .toList();

        double totalOrderQty = plans.stream().mapToDouble(ReplenishmentPlanResponse.ItemPlan::getRecommendedOrderQuantity).sum();
        int highRisk = (int) plans.stream().filter(p -> p.getRiskLevel() == RiskLevel.HIGH).count();
        int mediumRisk = (int) plans.stream().filter(p -> p.getRiskLevel() == RiskLevel.MEDIUM).count();
        int lowRisk = (int) plans.stream().filter(p -> p.getRiskLevel() == RiskLevel.LOW).count();

        return ReplenishmentPlanResponse.builder()
            .generatedAt(Instant.now())
            .itemCount(plans.size())
            .summary(ReplenishmentPlanResponse.Summary.builder()
                .totalRecommendedOrderQty(round(totalOrderQty))
                .highRiskItems(highRisk)
                .mediumRiskItems(mediumRisk)
                .lowRiskItems(lowRisk)
                .build())
            .items(plans)
            .build();
    }

    public PurchasePlanResponse purchasePlan(PurchasePlanRequest request) {
        List<PurchasePlanResponse.ItemPlan> itemPlans = new ArrayList<>();
        double totalPlannedQty = 0.0;
        double totalStockoutUnits = 0.0;
        int totalStockoutDays = 0;

        for (PlanningItemRequest item : request.getItems()) {
            PurchasePlanResponse.ItemPlan plan = buildPurchaseItemPlan(item, request);
            itemPlans.add(plan);
            totalPlannedQty += plan.getTotalPlannedOrderQty();
            totalStockoutUnits += plan.getProjectedStockoutUnits();
            totalStockoutDays += plan.getStockoutDays();
        }

        return PurchasePlanResponse.builder()
            .generatedAt(Instant.now())
            .horizonDays(request.getHorizonDays())
            .itemCount(itemPlans.size())
            .summary(PurchasePlanResponse.Summary.builder()
                .totalPlannedOrderQty(round(totalPlannedQty))
                .totalProjectedStockoutUnits(round(totalStockoutUnits))
                .totalStockoutDays(totalStockoutDays)
                .build())
            .items(itemPlans)
            .build();
    }

    public InventoryExceptionResponse detectExceptions(
            LocalDate fromDate, LocalDate toDate, int stockoutCoverageDays,
            int overstockCoverageDays, int limit) {

        LocalDate to = toDate != null ? toDate : LocalDate.now();
        LocalDate from = fromDate != null ? fromDate : to.minusDays(30);
        if (from.isAfter(to)) {
            throw new PlanningInputException("fromDate must be before or equal to toDate");
        }
        if (stockoutCoverageDays < 1 || overstockCoverageDays < 1 || limit < 1) {
            throw new PlanningInputException("coverage thresholds and limit must be >= 1");
        }

        List<PredictionRecord> records = predictionRepository.findByForecastDateBetweenOrderByForecastDateAsc(from, to);
        List<InventoryExceptionResponse.ExceptionItem> exceptions = new ArrayList<>();

        for (PredictionRecord record : records) {
            double inventoryPosition = record.getInventoryLevel() + record.getUnitsOrdered();
            double worstDemand = Math.max(EPS, record.getUpperBound() != null ? record.getUpperBound() : record.getPredictedDemand());
            double bestDemand = Math.max(EPS, record.getLowerBound() != null ? Math.max(EPS, record.getLowerBound()) : record.getPredictedDemand());
            double stockoutCoverage = inventoryPosition / worstDemand;
            double overstockCoverage = inventoryPosition / bestDemand;

            if (stockoutCoverage < stockoutCoverageDays) {
                double score = clamp((stockoutCoverageDays - stockoutCoverage) / stockoutCoverageDays, 0.0, 1.0);
                exceptions.add(toException(record, inventoryPosition, stockoutCoverage,
                    score, StockExceptionType.STOCKOUT_RISK,
                    "Increase replenishment frequency or expedite inbound supply."));
            }
            if (overstockCoverage > overstockCoverageDays) {
                double score = clamp((overstockCoverage - overstockCoverageDays) / overstockCoverageDays, 0.0, 1.0);
                exceptions.add(toException(record, inventoryPosition, overstockCoverage,
                    score, StockExceptionType.OVERSTOCK_RISK,
                    "Reduce order quantity or rebalance inventory across warehouses."));
            }
        }

        List<InventoryExceptionResponse.ExceptionItem> sorted = exceptions.stream()
            .sorted(Comparator.comparing(InventoryExceptionResponse.ExceptionItem::getRiskScore).reversed())
            .limit(limit)
            .toList();

        return InventoryExceptionResponse.builder()
            .generatedAt(Instant.now())
            .fromDate(from)
            .toDate(to)
            .exceptionCount(sorted.size())
            .exceptions(sorted)
            .build();
    }

    private ReplenishmentPlanResponse.ItemPlan buildReplenishmentItem(PlanningItemRequest item, double overstockFactor) {
        DemandStats stats = resolveDemandStats(item);
        InventoryPolicy policy = inventoryPolicy(item, stats);
        double inventoryPosition = item.getCurrentInventory() + item.getOnOrderInventory();
        double recommended = applyOrderConstraints(Math.max(0.0, policy.targetStockLevel() - inventoryPosition), item);
        double projectedCover = inventoryPosition / Math.max(EPS, stats.meanDailyDemand());

        double stockoutRisk = clamp((policy.reorderPoint() - inventoryPosition) / Math.max(policy.reorderPoint(), 1.0), 0.0, 1.0);
        double overstockRisk = clamp(
            (inventoryPosition - (policy.targetStockLevel() * overstockFactor))
                / Math.max(policy.targetStockLevel() * overstockFactor, 1.0),
            0.0,
            1.0
        );
        RiskLevel riskLevel = toRiskLevel(Math.max(stockoutRisk, overstockRisk));

        return ReplenishmentPlanResponse.ItemPlan.builder()
            .productId(item.getProductId())
            .warehouseId(item.getWarehouseId())
            .meanDailyDemand(round(stats.meanDailyDemand()))
            .demandStdDev(round(stats.stdDailyDemand()))
            .demandCv(round(stats.cv()))
            .safetyStock(round(policy.safetyStock()))
            .reorderPoint(round(policy.reorderPoint()))
            .targetStockLevel(round(policy.targetStockLevel()))
            .inventoryPosition(round(inventoryPosition))
            .projectedCoverDays(round(projectedCover))
            .recommendedOrderQuantity(round(recommended))
            .riskLevel(riskLevel)
            .stockoutRiskScore(round(stockoutRisk))
            .overstockRiskScore(round(overstockRisk))
            .build();
    }

    private PurchasePlanResponse.ItemPlan buildPurchaseItemPlan(PlanningItemRequest item, PurchasePlanRequest request) {
        DemandStats stats = resolveDemandStats(item);
        InventoryPolicy policy = inventoryPolicy(item, stats);
        List<Double> forecast = buildForecastSeries(item, request.getHorizonDays(), stats.meanDailyDemand());
        List<PurchasePlanResponse.OrderLine> orders = new ArrayList<>();
        Map<Integer, Double> receipts = new HashMap<>();

        if (item.getOnOrderInventory() > 0) {
            int inboundDay = Math.max(0, item.getLeadTimeDays() - 1);
            receipts.merge(inboundDay, item.getOnOrderInventory(), Double::sum);
        }

        double inventory = item.getCurrentInventory();
        double totalForecastDemand = 0.0;
        double totalPlannedOrderQty = 0.0;
        double stockoutUnits = 0.0;
        int stockoutDays = 0;
        int overstockDays = 0;

        for (int day = 0; day < request.getHorizonDays(); day++) {
            inventory += receipts.getOrDefault(day, 0.0);
            double dailyDemand = forecast.get(day);
            totalForecastDemand += dailyDemand;
            inventory -= dailyDemand;
            if (inventory < 0) {
                stockoutUnits += -inventory;
                stockoutDays++;
                inventory = 0.0;
            }
            if (inventory > policy.targetStockLevel() * request.getOverstockFactor()) {
                overstockDays++;
            }

            boolean reviewDay = day % Math.max(1, item.getReviewPeriodDays()) == 0;
            if (!reviewDay || orders.size() >= request.getMaxOrdersPerItem()) {
                continue;
            }

            double pipeline = inventory;
            for (Map.Entry<Integer, Double> entry : receipts.entrySet()) {
                if (entry.getKey() > day) {
                    pipeline += entry.getValue();
                }
            }

            if (pipeline < policy.reorderPoint()) {
                double orderQty = applyOrderConstraints(policy.targetStockLevel() - pipeline, item);
                if (orderQty > 0) {
                    int arrivalDay = day + item.getLeadTimeDays();
                    receipts.merge(arrivalDay, orderQty, Double::sum);
                    totalPlannedOrderQty += orderQty;
                    orders.add(PurchasePlanResponse.OrderLine.builder()
                        .orderDay(day)
                        .arrivalDay(arrivalDay)
                        .quantity(round(orderQty))
                        .reason("Inventory position below reorder point")
                        .build());
                }
            }
        }

        double serviceLevelEstimate = clamp(1.0 - ((double) stockoutDays / Math.max(1, request.getHorizonDays())), 0.0, 1.0);
        double endingInventory = inventory;
        RiskLevel risk = toRiskLevel(Math.max(
            clamp(stockoutUnits / Math.max(totalForecastDemand, 1.0), 0.0, 1.0),
            clamp((double) overstockDays / Math.max(1, request.getHorizonDays()), 0.0, 1.0)
        ));

        return PurchasePlanResponse.ItemPlan.builder()
            .productId(item.getProductId())
            .warehouseId(item.getWarehouseId())
            .totalForecastDemand(round(totalForecastDemand))
            .totalPlannedOrderQty(round(totalPlannedOrderQty))
            .projectedEndingInventory(round(endingInventory))
            .projectedStockoutUnits(round(stockoutUnits))
            .stockoutDays(stockoutDays)
            .overstockDays(overstockDays)
            .riskLevel(risk)
            .serviceLevelEstimate(round(serviceLevelEstimate))
            .orders(orders)
            .build();
    }

    private InventoryExceptionResponse.ExceptionItem toException(
            PredictionRecord record, double inventoryPosition, double coverageDays,
            double riskScore, StockExceptionType type, String recommendation) {
        return InventoryExceptionResponse.ExceptionItem.builder()
            .predictionId(record.getId())
            .productId(record.getCategory())
            .warehouseId(record.getRegion())
            .category(record.getCategory())
            .region(record.getRegion())
            .forecastDate(record.getForecastDate())
            .type(type)
            .riskLevel(toRiskLevel(riskScore))
            .predictedDemand(round(record.getPredictedDemand()))
            .inventoryPosition(round(inventoryPosition))
            .coverageDays(round(coverageDays))
            .riskScore(round(riskScore))
            .recommendation(recommendation)
            .build();
    }

    private DemandStats resolveDemandStats(PlanningItemRequest item) {
        Double mean = item.getForecastMeanDaily();
        Double std = item.getForecastStdDaily();
        List<Double> source = null;

        if (item.getDemandForecast() != null && !item.getDemandForecast().isEmpty()) {
            source = item.getDemandForecast();
        } else if (item.getDemandHistory() != null && !item.getDemandHistory().isEmpty()) {
            source = item.getDemandHistory();
        }

        if (mean == null && source != null) {
            mean = source.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        if (std == null && source != null) {
            double avg = source.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double var = source.stream().mapToDouble(v -> (v - avg) * (v - avg)).average().orElse(0.0);
            std = Math.sqrt(var);
        }

        if (mean == null || mean <= 0.0) {
            throw new PlanningInputException(
                "Item [" + item.getProductId() + "@" + item.getWarehouseId() + "] requires a positive forecastMeanDaily or demand history."
            );
        }
        if (std == null || std < 0.0) {
            std = Math.max(0.0, mean * 0.25);
        }
        double cv = mean > EPS ? std / mean : 0.0;
        return new DemandStats(mean, std, cv);
    }

    private InventoryPolicy inventoryPolicy(PlanningItemRequest item, DemandStats stats) {
        double z = inverseStandardNormal(item.getServiceLevel());
        double protectionDays = item.getLeadTimeDays() + item.getReviewPeriodDays();
        double safetyStock = z * stats.stdDailyDemand() * Math.sqrt(Math.max(1.0, protectionDays));
        double reorderPoint = stats.meanDailyDemand() * item.getLeadTimeDays() + safetyStock;
        double targetStock = stats.meanDailyDemand() * protectionDays + safetyStock;
        return new InventoryPolicy(safetyStock, reorderPoint, targetStock);
    }

    private List<Double> buildForecastSeries(PlanningItemRequest item, int horizonDays, double fallbackMean) {
        if (item.getDemandForecast() == null || item.getDemandForecast().isEmpty()) {
            List<Double> fallback = new ArrayList<>();
            for (int i = 0; i < horizonDays; i++) {
                fallback.add(fallbackMean);
            }
            return fallback;
        }
        if (item.getDemandForecast().size() >= horizonDays) {
            return new ArrayList<>(item.getDemandForecast().subList(0, horizonDays));
        }
        List<Double> forecast = new ArrayList<>(item.getDemandForecast());
        while (forecast.size() < horizonDays) {
            forecast.add(fallbackMean);
        }
        return forecast;
    }

    private double applyOrderConstraints(double proposedQty, PlanningItemRequest item) {
        if (proposedQty <= 0.0) {
            return 0.0;
        }
        double qty = proposedQty;
        double minOrder = item.getMinOrderQuantity() != null ? item.getMinOrderQuantity() : 0.0;
        Double maxOrder = item.getMaxOrderQuantity();
        Double multiple = item.getOrderMultiple();

        if (minOrder > 0.0 && qty > 0.0 && qty < minOrder) {
            qty = minOrder;
        }
        if (multiple != null && multiple > 0.0) {
            qty = Math.ceil(qty / multiple) * multiple;
        }
        if (maxOrder != null && maxOrder > 0.0) {
            qty = Math.min(qty, maxOrder);
        }
        return qty;
    }

    private RiskLevel toRiskLevel(double score) {
        if (score >= 0.66) {
            return RiskLevel.HIGH;
        }
        if (score >= 0.33) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // Acklam's inverse normal CDF approximation.
    private double inverseStandardNormal(double p) {
        if (p <= 0.0 || p >= 1.0) {
            throw new PlanningInputException("serviceLevel must be strictly between 0 and 1");
        }

        double[] a = {-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
            1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00};
        double[] b = {-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
            6.680131188771972e+01, -1.328068155288572e+01};
        double[] c = {-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
            -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00};
        double[] d = {7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00};

        double q, r;
        if (p < 0.02425) {
            q = Math.sqrt(-2.0 * Math.log(p));
            return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
        } else if (p > 1.0 - 0.02425) {
            q = Math.sqrt(-2.0 * Math.log(1.0 - p));
            return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
        }

        q = p - 0.5;
        r = q * q;
        return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
            (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0);
    }

    private record DemandStats(double meanDailyDemand, double stdDailyDemand, double cv) {}
    private record InventoryPolicy(double safetyStock, double reorderPoint, double targetStockLevel) {}
}
