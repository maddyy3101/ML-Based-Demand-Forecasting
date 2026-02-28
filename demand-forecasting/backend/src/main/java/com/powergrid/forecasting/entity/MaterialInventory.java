package com.powergrid.forecasting.entity;

import com.powergrid.forecasting.enums.MaterialType;
import com.powergrid.forecasting.enums.Region;
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
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "material_inventory",
        indexes = {
            @Index(name = "idx_inventory_region_material", columnList = "region,material_type")
        }
)
public class MaterialInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 40)
    private MaterialType materialType;

    @Column(nullable = false, length = 80)
    private String materialName;

    @Column(nullable = false, length = 40)
    private String unitLabel;

    @Column(nullable = false, unique = true, length = 80)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TowerType towerType;

    @Column(nullable = false)
    private int currentStock;

    @Column(nullable = false)
    private int reorderThreshold;

    @Column(nullable = false)
    private int maxCapacity;

    @Column(nullable = false)
    private double unitCostInr;

    @Column(nullable = false, length = 120)
    private String warehouseLocation;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant lastUpdated;
}
