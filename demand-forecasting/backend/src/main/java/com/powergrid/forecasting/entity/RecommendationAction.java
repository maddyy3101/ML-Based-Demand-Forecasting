package com.powergrid.forecasting.entity;

import com.powergrid.forecasting.enums.RecommendationActionType;
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
        name = "recommendation_actions",
        indexes = {
            @Index(name = "idx_rec_action_inventory_user_time", columnList = "inventory_id,acted_by,created_at"),
            @Index(name = "idx_rec_action_user_time", columnList = "acted_by,created_at")
        }
)
public class RecommendationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private MaterialInventory inventory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecommendationActionType actionType;

    @Column(nullable = false)
    private int recommendedOrderQty;

    @Column(length = 400)
    private String note;

    @Column(name = "acted_by", nullable = false, length = 60)
    private String actedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
