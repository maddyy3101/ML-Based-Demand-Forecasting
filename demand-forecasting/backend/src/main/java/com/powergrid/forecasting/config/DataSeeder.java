package com.powergrid.forecasting.config;

import com.powergrid.forecasting.entity.MaterialInventory;
import com.powergrid.forecasting.entity.PowerGridUser;
import com.powergrid.forecasting.enums.MaterialType;
import com.powergrid.forecasting.enums.Region;
import com.powergrid.forecasting.enums.Role;
import com.powergrid.forecasting.enums.TowerType;
import com.powergrid.forecasting.repository.MaterialInventoryRepository;
import com.powergrid.forecasting.repository.PowerGridUserRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final PowerGridUserRepository userRepository;
    private final MaterialInventoryRepository inventoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean resetDefaultUserPasswords;

    public DataSeeder(
            PowerGridUserRepository userRepository,
            MaterialInventoryRepository inventoryRepository,
            PasswordEncoder passwordEncoder,
            @Value("${powergrid.seed.reset-default-passwords:true}") boolean resetDefaultUserPasswords
    ) {
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.resetDefaultUserPasswords = resetDefaultUserPasswords;
    }

    @Override
    public void run(String... args) {
        seedUsers();
        seedInventory();
    }

    private void seedUsers() {
        List<SeedUser> defaults = List.of(
                new SeedUser("hq_admin", "admin@powergrid.in", "pgAdmin@2025", Role.ROLE_ADMIN, null, "PG-HQ-001"),
                new SeedUser("proc_north", "procurement.north@powergrid.in", "procN@2025", Role.ROLE_PROCUREMENT_OFFICER, "North", "PG-PROC-N-001"),
                new SeedUser("proc_south", "procurement.south@powergrid.in", "procS@2025", Role.ROLE_PROCUREMENT_OFFICER, "South", "PG-PROC-S-001"),
                new SeedUser("site_raj", "site.rajasthan@powergrid.in", "siteR@2025", Role.ROLE_SITE_MANAGER, "North", "PG-SITE-N-RAJ"),
                new SeedUser("site_kar", "site.karnataka@powergrid.in", "siteK@2025", Role.ROLE_SITE_MANAGER, "South", "PG-SITE-S-KAR")
        );

        defaults.forEach(this::upsertUser);
    }

    private void seedInventory() {
        if (inventoryRepository.count() > 0) {
            return;
        }

        saveInventory(MaterialType.CONDUCTOR, "PG-COND-NORTH-001", Region.NORTH, TowerType.KV_400, 2500, 1000, 8000, 450.0, "Lucknow, UP");
        saveInventory(MaterialType.TRANSFORMER, "PG-TRANS-NORTH-001", Region.NORTH, TowerType.KV_765, 15, 10, 80, 4200000.0, "Lucknow, UP");
        saveInventory(MaterialType.CEMENT, "PG-CEM-NORTH-001", Region.NORTH, TowerType.KV_220, 3000, 2000, 20000, 380.0, "Lucknow, UP");

        saveInventory(MaterialType.STEEL, "PG-STEEL-SOUTH-001", Region.SOUTH, TowerType.KV_400, 800, 1200, 10000, 65000.0, "Bengaluru, KA");
        saveInventory(MaterialType.INSULATOR, "PG-INSUL-SOUTH-001", Region.SOUTH, TowerType.KV_765, 600, 500, 5000, 1200.0, "Bengaluru, KA");
        saveInventory(MaterialType.CONDUCTOR, "PG-COND-SOUTH-001", Region.SOUTH, TowerType.KV_220, 1800, 800, 6000, 450.0, "Bengaluru, KA");

        saveInventory(MaterialType.CEMENT, "PG-CEM-EAST-001", Region.EAST, TowerType.KV_220, 400, 1500, 15000, 380.0, "Kolkata, WB");
        saveInventory(MaterialType.TRANSFORMER, "PG-TRANS-EAST-001", Region.EAST, TowerType.KV_400, 8, 8, 60, 4200000.0, "Kolkata, WB");

        saveInventory(MaterialType.STEEL, "PG-STEEL-WEST-001", Region.WEST, TowerType.KV_765, 5200, 1000, 12000, 65000.0, "Mumbai, MH");
        saveInventory(MaterialType.INSULATOR, "PG-INSUL-WEST-001", Region.WEST, TowerType.KV_400, 320, 400, 4000, 1200.0, "Mumbai, MH");
    }

    private void upsertUser(SeedUser seed) {
        PowerGridUser user = userRepository.findByUsernameIgnoreCase(seed.username())
                .orElseGet(PowerGridUser::new);

        user.setUsername(seed.username());
        user.setEmail(seed.email());
        user.setRole(seed.role());
        user.setAssignedRegion(seed.assignedRegion());
        user.setEmployeeId(seed.employeeId());
        user.setActive(true);

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank() || resetDefaultUserPasswords) {
            user.setPasswordHash(passwordEncoder.encode(seed.password()));
        }
        userRepository.save(user);
    }

    private void saveInventory(
            MaterialType materialType,
            String sku,
            Region region,
            TowerType towerType,
            int currentStock,
            int reorderThreshold,
            int maxCapacity,
            double unitCost,
            String location
    ) {
        MaterialInventory inventory = new MaterialInventory();
        inventory.setMaterialType(materialType);
        inventory.setMaterialName(materialType.getDisplayName());
        inventory.setUnitLabel(materialType.getUnitLabel());
        inventory.setSku(sku);
        inventory.setRegion(region);
        inventory.setTowerType(towerType);
        inventory.setCurrentStock(currentStock);
        inventory.setReorderThreshold(reorderThreshold);
        inventory.setMaxCapacity(maxCapacity);
        inventory.setUnitCostInr(unitCost);
        inventory.setWarehouseLocation(location);
        inventoryRepository.save(inventory);
    }

    private record SeedUser(
            String username,
            String email,
            String password,
            Role role,
            String assignedRegion,
            String employeeId
    ) {
    }
}
