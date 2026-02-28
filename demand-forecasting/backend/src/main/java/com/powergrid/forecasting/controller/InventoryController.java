package com.powergrid.forecasting.controller;

import com.powergrid.forecasting.dto.inventory.InventoryItemDto;
import com.powergrid.forecasting.dto.inventory.MovementDto;
import com.powergrid.forecasting.dto.inventory.MovementRequest;
import com.powergrid.forecasting.dto.inventory.ProcurementRecommendationDto;
import com.powergrid.forecasting.dto.inventory.RecommendationActionRequest;
import com.powergrid.forecasting.dto.inventory.RecommendationActionResponse;
import com.powergrid.forecasting.entity.PowerGridUser;
import com.powergrid.forecasting.enums.Role;
import com.powergrid.forecasting.exception.ResourceNotFoundException;
import com.powergrid.forecasting.repository.PowerGridUserRepository;
import com.powergrid.forecasting.service.InventoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final PowerGridUserRepository userRepository;

    public InventoryController(InventoryService inventoryService, PowerGridUserRepository userRepository) {
        this.inventoryService = inventoryService;
        this.userRepository = userRepository;
    }

    @GetMapping("/items")
    public List<InventoryItemDto> items(Authentication authentication) {
        PowerGridUser user = getCurrent(authentication);
        boolean isAdmin = user.getRole() == Role.ROLE_ADMIN;
        return inventoryService.getInventoryItems(user.getAssignedRegion(), isAdmin);
    }

    @PostMapping("/movement")
    public MovementDto movement(@Valid @RequestBody MovementRequest request, Authentication authentication) {
        PowerGridUser user = getCurrent(authentication);
        return inventoryService.logMovement(request, user);
    }

    @GetMapping("/movements")
    public List<MovementDto> movements(Authentication authentication) {
        PowerGridUser user = getCurrent(authentication);
        boolean isAdmin = user.getRole() == Role.ROLE_ADMIN;
        return inventoryService.getMovements(user.getAssignedRegion(), isAdmin);
    }

    @GetMapping("/recommendations")
    public List<ProcurementRecommendationDto> recommendations(Authentication authentication) {
        PowerGridUser user = getCurrent(authentication);
        String scopeRegion = user.getRole() == Role.ROLE_ADMIN ? null : user.getAssignedRegion();
        return inventoryService.getRecommendations(scopeRegion, user.getUsername());
    }

    @PostMapping("/recommendations/{inventoryId}/raise")
    public RecommendationActionResponse raiseRecommendation(
            @PathVariable String inventoryId,
            @RequestBody(required = false) RecommendationActionRequest request,
            Authentication authentication
    ) {
        PowerGridUser user = getCurrent(authentication);
        RecommendationActionRequest payload = request == null
                ? new RecommendationActionRequest(null, null)
                : request;
        return inventoryService.raiseRecommendation(inventoryId, payload, user);
    }

    @PostMapping("/recommendations/{inventoryId}/dismiss")
    public RecommendationActionResponse dismissRecommendation(
            @PathVariable String inventoryId,
            @RequestBody(required = false) RecommendationActionRequest request,
            Authentication authentication
    ) {
        PowerGridUser user = getCurrent(authentication);
        RecommendationActionRequest payload = request == null
                ? new RecommendationActionRequest(null, null)
                : request;
        return inventoryService.dismissRecommendation(inventoryId, payload, user);
    }

    @GetMapping("/recommendations/actions")
    public List<RecommendationActionResponse> recommendationActions(Authentication authentication) {
        PowerGridUser user = getCurrent(authentication);
        boolean includeAll = user.getRole() == Role.ROLE_ADMIN;
        return inventoryService.getRecommendationActions(user.getUsername(), includeAll);
    }

    private PowerGridUser getCurrent(Authentication authentication) {
        return userRepository.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }
}
