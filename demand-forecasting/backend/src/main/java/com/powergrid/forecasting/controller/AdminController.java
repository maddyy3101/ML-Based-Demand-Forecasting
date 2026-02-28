package com.powergrid.forecasting.controller;

import com.powergrid.forecasting.dto.admin.RetrainingStatusDto;
import com.powergrid.forecasting.dto.admin.SystemHealthDto;
import com.powergrid.forecasting.dto.admin.UploadResponseDto;
import com.powergrid.forecasting.dto.admin.UserSummaryDto;
import com.powergrid.forecasting.dto.admin.UserUpdateRequest;
import com.powergrid.forecasting.dto.auth.RegisterRequest;
import com.powergrid.forecasting.entity.PowerGridUser;
import com.powergrid.forecasting.entity.ProcurementForecast;
import com.powergrid.forecasting.exception.ResourceNotFoundException;
import com.powergrid.forecasting.repository.PowerGridUserRepository;
import com.powergrid.forecasting.service.AdminService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final PowerGridUserRepository userRepository;

    public AdminController(AdminService adminService, PowerGridUserRepository userRepository) {
        this.adminService = adminService;
        this.userRepository = userRepository;
    }

    @PostMapping("/upload-dataset")
    public UploadResponseDto uploadDataset(@RequestParam("file") MultipartFile file, Authentication authentication) {
        PowerGridUser current = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
        return adminService.uploadDataset(file, current);
    }

    @GetMapping("/retraining-status/{id}")
    public RetrainingStatusDto retrainingStatus(@PathVariable UUID id) {
        return adminService.getRetrainingStatus(id);
    }

    @GetMapping("/users")
    public List<UserSummaryDto> users() {
        return adminService.getUsers();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummaryDto createUser(@Valid @RequestBody RegisterRequest request) {
        return adminService.createUser(request);
    }

    @PutMapping("/users/{id}")
    public UserSummaryDto updateUser(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request) {
        return adminService.updateUser(id, request);
    }

    @DeleteMapping("/users/{id}")
    public void deactivateUser(@PathVariable UUID id) {
        adminService.deactivateUser(id);
    }

    @GetMapping("/system-health")
    public SystemHealthDto systemHealth() {
        return adminService.getSystemHealth();
    }

    @GetMapping("/audit-log")
    public Page<ProcurementForecast> auditLog(@PageableDefault(size = 25) Pageable pageable) {
        return adminService.getAuditLog(pageable);
    }
}
