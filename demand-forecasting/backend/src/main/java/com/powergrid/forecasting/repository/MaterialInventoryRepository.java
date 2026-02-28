package com.powergrid.forecasting.repository;

import com.powergrid.forecasting.entity.MaterialInventory;
import com.powergrid.forecasting.enums.MaterialType;
import com.powergrid.forecasting.enums.Region;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialInventoryRepository extends JpaRepository<MaterialInventory, UUID> {
    List<MaterialInventory> findByRegion(Region region);
    List<MaterialInventory> findByRegionAndMaterialType(Region region, MaterialType materialType);
    Optional<MaterialInventory> findBySku(String sku);
}
