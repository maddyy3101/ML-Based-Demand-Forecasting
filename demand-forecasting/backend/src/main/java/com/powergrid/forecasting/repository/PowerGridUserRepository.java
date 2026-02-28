package com.powergrid.forecasting.repository;

import com.powergrid.forecasting.entity.PowerGridUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PowerGridUserRepository extends JpaRepository<PowerGridUser, UUID> {
    Optional<PowerGridUser> findByUsername(String username);
    Optional<PowerGridUser> findByEmail(String email);
    Optional<PowerGridUser> findByUsernameIgnoreCase(String username);
    Optional<PowerGridUser> findByEmailIgnoreCase(String email);
}
