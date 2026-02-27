package com.demandforecast.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "prediction_records",
    indexes = {
        @Index(name = "idx_pred_date",     columnList = "forecast_date"),
        @Index(name = "idx_pred_category", columnList = "category"),
        @Index(name = "idx_pred_region",   columnList = "region"),
        @Index(name = "idx_pred_created",  columnList = "created_at"),
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "forecast_date", nullable = false)
    private LocalDate forecastDate;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(name = "inventory_level")
    private int inventoryLevel;

    @Column(name = "units_ordered")
    private int unitsOrdered;

    private double price;
    private double discount;

    @Column(name = "weather_condition", length = 50)
    private String weatherCondition;

    private boolean promotion;

    @Column(name = "competitor_pricing")
    private double competitorPricing;

    @Column(length = 20)
    private String seasonality;

    private boolean epidemic;

    @Column(name = "predicted_demand", nullable = false)
    private double predictedDemand;

    @Column(name = "actual_demand")
    private Double actualDemand;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "request_id", length = 64)
    private String requestId;
}
