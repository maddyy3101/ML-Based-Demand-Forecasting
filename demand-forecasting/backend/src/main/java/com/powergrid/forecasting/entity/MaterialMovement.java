package com.powergrid.forecasting.entity;

import com.powergrid.forecasting.enums.MovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
        name = "material_movements",
        indexes = {
            @Index(name = "idx_movement_inventory_time", columnList = "inventory_id,timestamp")
        }
)
public class MaterialMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private MaterialInventory inventory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType movementType;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 180)
    private String reason;

    @Column(length = 40)
    private String projectId;

    @Column(length = 120)
    private String vendorName;

    @Column(length = 120)
    private String invoiceNumber;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private PowerGridUser performedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant timestamp;
}
