package com.powergrid.forecasting.repository;

import com.powergrid.forecasting.entity.RecommendationAction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationActionRepository extends JpaRepository<RecommendationAction, UUID> {

    List<RecommendationAction> findByInventoryIdInAndActedByOrderByCreatedAtDesc(List<UUID> inventoryIds, String actedBy);

    List<RecommendationAction> findTop100ByOrderByCreatedAtDesc();

    List<RecommendationAction> findTop100ByActedByOrderByCreatedAtDesc(String actedBy);
}
