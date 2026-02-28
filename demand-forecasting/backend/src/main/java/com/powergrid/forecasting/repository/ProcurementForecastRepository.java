package com.powergrid.forecasting.repository;

import com.powergrid.forecasting.entity.ProcurementForecast;
import com.powergrid.forecasting.enums.MaterialType;
import com.powergrid.forecasting.enums.ProjectPhase;
import com.powergrid.forecasting.enums.Region;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcurementForecastRepository extends JpaRepository<ProcurementForecast, UUID> {
    List<ProcurementForecast> findByRequestedByOrderByCreatedAtDesc(String username);

    List<ProcurementForecast> findByMaterialTypeAndForecastYearAndForecastMonth(
            MaterialType materialType,
            int year,
            int month
    );

    long countByCreatedAtAfter(Instant after);

    Page<ProcurementForecast> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ProcurementForecast> findByMaterialTypeAndRegionAndProjectPhaseOrderByCreatedAtDesc(
            MaterialType materialType,
            Region region,
            ProjectPhase projectPhase,
            Pageable pageable
    );

    Optional<ProcurementForecast> findTopByMaterialTypeAndRegionOrderByCreatedAtDesc(MaterialType materialType, Region region);
}
