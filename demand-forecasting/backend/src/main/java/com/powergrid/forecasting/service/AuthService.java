package com.powergrid.forecasting.service;

import com.powergrid.forecasting.dto.admin.UserSummaryDto;
import com.powergrid.forecasting.dto.auth.LoginRequest;
import com.powergrid.forecasting.dto.auth.LoginResponse;
import com.powergrid.forecasting.dto.auth.RegisterRequest;
import com.powergrid.forecasting.entity.PowerGridUser;
import com.powergrid.forecasting.enums.Role;
import com.powergrid.forecasting.exception.ValidationFailureException;
import com.powergrid.forecasting.repository.PowerGridUserRepository;
import com.powergrid.forecasting.security.JwtTokenProvider;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final PowerGridUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            AuthenticationManager authenticationManager,
            PowerGridUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResponse login(LoginRequest request) {
        String loginId = request.username() == null ? "" : request.username().trim();
        String password = request.password() == null ? "" : request.password();
        if (loginId.isBlank() || password.isBlank()) {
            throw new ValidationFailureException("Username/email and password are required.");
        }

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginId, password)
        );
        PowerGridUser user = (PowerGridUser) auth.getPrincipal();
        if (!user.isActive()) {
            throw new ValidationFailureException("User account is deactivated.");
        }
        String token = jwtTokenProvider.generateToken(user);
        return new LoginResponse(
                token,
                user.getRole().name(),
                user.getUsername(),
                user.getId(),
                user.getAssignedRegion(),
                user.getEmployeeId(),
                jwtTokenProvider.getExpirationMs()
        );
    }

    public UserSummaryDto register(RegisterRequest request) {
        String username = request.username() == null ? "" : request.username().trim().toLowerCase();
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        String assignedRegion = normalizeRegion(request.assignedRegion());

        if (username.isBlank()) {
            throw new ValidationFailureException("Username is required.");
        }
        if (email.isBlank()) {
            throw new ValidationFailureException("Email is required.");
        }
        if (request.role() != Role.ROLE_ADMIN && (assignedRegion == null || assignedRegion.isBlank())) {
            throw new ValidationFailureException("Assigned region is required for procurement officer and site manager.");
        }

        userRepository.findByUsernameIgnoreCase(username).ifPresent(existing -> {
            throw new ValidationFailureException("Username already exists.");
        });
        userRepository.findByEmailIgnoreCase(email).ifPresent(existing -> {
            throw new ValidationFailureException("Email already exists.");
        });

        PowerGridUser user = new PowerGridUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setAssignedRegion(request.role() == Role.ROLE_ADMIN ? null : assignedRegion);
        user.setEmployeeId(request.employeeId() == null ? "PG-" + UUID.randomUUID().toString().substring(0, 8) : request.employeeId());
        user.setActive(true);

        PowerGridUser saved = userRepository.save(user);
        return toSummary(saved);
    }

    public UserSummaryDto getCurrentUser(String username) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isBlank()) {
            throw new ValidationFailureException("Username is required.");
        }
        PowerGridUser user = userRepository.findByUsernameIgnoreCase(normalized)
                .orElseThrow(() -> new ValidationFailureException("User not found."));
        return toSummary(user);
    }

    public UserSummaryDto toSummary(PowerGridUser user) {
        return new UserSummaryDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getAssignedRegion(),
                user.getEmployeeId(),
                user.isActive(),
                user.getCreatedAt()
        );
    }

    private String normalizeRegion(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        String lower = trimmed.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
