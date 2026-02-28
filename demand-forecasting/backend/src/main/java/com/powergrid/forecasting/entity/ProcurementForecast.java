package com.powergrid.forecasting.entity;

import com.powergrid.forecasting.enums.MaterialType;
import com.powergrid.forecasting.enums.ProcurementDecision;
import com.powergrid.forecasting.enums.ProjectPhase;
import com.powergrid.forecasting.enums.Region;
import com.powergrid.forecasting.enums.SubstationType;
import com.powergrid.forecasting.enums.TerrainType;
import com.powergrid.forecasting.enums.TowerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "procurement_forecasts",
        indexes = {
            @Index(name = "idx_forecast_material_period", columnList = "material_type,forecast_year,forecast_month"),
            @Index(name = "idx_forecast_requester_time", columnList = "requested_by,created_at"),
            @Index(name = "idx_forecast_region_material", columnList = "region,material_type")
        }
)
public class ProcurementForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String requestId;

    @Column(nullable = false, length = 40)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProjectPhase projectPhase;

    @Column(nullable = false, length = 60)
    private String state;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TerrainType terrainType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TowerType towerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubstationType substationType;

    @Column(nullable = false)
    private int transmissionLengthKm;

    @Column(nullable = false)
    private int budgetCrore;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 30)
    private MaterialType materialType;

    @Column(nullable = false)
    private int leadTimeDays;

    @Column(nullable = false)
    private double taxPercentage;

    @Column(nullable = false)
    private double transportationCost;

    @Column(nullable = false)
    private double historicalConsumption;

    @Column(nullable = false)
    private int forecastMonth;

    @Column(nullable = false)
    private int forecastYear;

    @Column(nullable = false)
    private int predictedQuantity;

    private Integer actualQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProcurementDecision procurementDecision;

    @Column(nullable = false, length = 400)
    private String decisionMessage;

    @Column(nullable = false)
    private double modelConfidence;

    @Column(name = "requested_by", nullable = false, length = 50)
    private String requestedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
