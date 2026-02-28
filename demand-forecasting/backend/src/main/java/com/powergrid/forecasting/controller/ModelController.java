package com.powergrid.forecasting.controller;

import com.powergrid.forecasting.dto.ActivateModelRequest;
import com.powergrid.forecasting.service.ModelService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/models")
public class ModelController {

    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping("/active")
    public Map<String, Object> active() {
        return modelService.getActiveModel();
    }

    @PostMapping("/{version}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> activate(
            @PathVariable String version,
            @RequestBody(required = false) ActivateModelRequest request
    ) {
        return modelService.activate(version, request == null ? null : request.reason());
    }
}
