package com.powergrid.forecasting.entity;

import com.powergrid.forecasting.enums.RetrainingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "retraining_jobs")
public class RetrainingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 260)
    private String datasetPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RetrainingStatus status;

    private Instant startedAt;

    private Instant completedAt;

    @Column(columnDefinition = "TEXT")
    private String logOutput;

    private int rowCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by")
    private PowerGridUser triggeredBy;

    @Column(columnDefinition = "TEXT")
    private String modelMetricsJson;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
