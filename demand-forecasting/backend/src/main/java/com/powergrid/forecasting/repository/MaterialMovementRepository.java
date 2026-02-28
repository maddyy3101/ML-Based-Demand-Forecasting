package com.powergrid.forecasting.repository;

import com.powergrid.forecasting.entity.MaterialMovement;
import com.powergrid.forecasting.enums.MovementType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialMovementRepository extends JpaRepository<MaterialMovement, UUID> {
    List<MaterialMovement> findByInventoryIdAndTimestampAfter(UUID inventoryId, Instant after);
    List<MaterialMovement> findByMovementTypeAndTimestampAfter(MovementType movementType, Instant after);
    List<MaterialMovement> findByInventoryIdOrderByTimestampDesc(UUID inventoryId);
}
