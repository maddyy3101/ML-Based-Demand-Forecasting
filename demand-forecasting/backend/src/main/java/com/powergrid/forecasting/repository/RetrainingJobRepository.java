package com.powergrid.forecasting.repository;

import com.powergrid.forecasting.entity.RetrainingJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetrainingJobRepository extends JpaRepository<RetrainingJob, UUID> {
    Optional<RetrainingJob> findTopByOrderByCreatedAtDesc();
}
