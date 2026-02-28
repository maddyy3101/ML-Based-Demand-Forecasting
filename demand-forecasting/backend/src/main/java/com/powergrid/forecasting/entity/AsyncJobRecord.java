package com.powergrid.forecasting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "async_jobs",
        indexes = {
            @Index(name = "idx_async_jobs_created_by_time", columnList = "created_by,created_at"),
            @Index(name = "idx_async_jobs_status_time", columnList = "status,created_at")
        }
)
public class AsyncJobRecord {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 250)
    private String message;

    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "created_by", length = 60)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
