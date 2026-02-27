package com.demandforecast.service;

import com.demandforecast.client.MlApiClient;
import com.demandforecast.dto.ActivateModelRequest;
import com.demandforecast.dto.ModelVersionResponse;
import com.demandforecast.exception.ModelActivationException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ModelRegistryService {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,64}$");

    private final MlApiClient mlApiClient;
    private final ConcurrentHashMap<String, ModelVersionState> versions = new ConcurrentHashMap<>();
    private final AtomicReference<String> activeVersion = new AtomicReference<>();

    @PostConstruct
    void init() {
        Map<String, Object> metadata = fetchMlMetadata();
        ModelVersionState initial = new ModelVersionState("v1", true, Instant.now(), "system", metadata);
        versions.put("v1", initial);
        activeVersion.set("v1");
    }

    public ModelVersionResponse getActiveModel() {
        String version = activeVersion.get();
        ModelVersionState state = versions.get(version);
        if (state == null) {
            throw new ModelActivationException("No active model version is currently registered.");
        }
        Map<String, Object> merged = new LinkedHashMap<>(state.metadata());
        merged.put("liveMlInfo", fetchMlMetadata());
        return toResponse(state, merged);
    }

    public ModelVersionResponse activate(String version, ActivateModelRequest request, String requestId) {
        if (version == null || version.isBlank() || !VERSION_PATTERN.matcher(version).matches()) {
            throw new ModelActivationException("Model version must match ^[a-zA-Z0-9._-]{1,64}$");
        }

        String actor = request != null && request.getActivatedBy() != null && !request.getActivatedBy().isBlank()
            ? request.getActivatedBy()
            : requestId;

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (request != null && request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }
        metadata.put("liveMlInfoAtActivation", fetchMlMetadata());

        versions.forEach((k, v) -> versions.put(k, v.withActive(false)));
        ModelVersionState updated = new ModelVersionState(version, true, Instant.now(), actor, metadata);
        versions.put(version, updated);
        activeVersion.set(version);

        return toResponse(updated, updated.metadata());
    }

    public String getActiveVersion() {
        return activeVersion.get();
    }

    private Map<String, Object> fetchMlMetadata() {
        try {
            Map<String, Object> info = mlApiClient.getModelInfo()
                .block(Duration.ofSeconds(3));
            return info != null ? info : Map.of();
        } catch (Exception ex) {
            return Map.of("status", "unavailable", "reason", ex.getClass().getSimpleName());
        }
    }

    private ModelVersionResponse toResponse(ModelVersionState state, Map<String, Object> metadata) {
        return ModelVersionResponse.builder()
            .version(state.version())
            .active(state.active())
            .activatedAt(state.activatedAt())
            .activatedBy(state.activatedBy())
            .metadata(metadata)
            .build();
    }

    private record ModelVersionState(
        String version,
        boolean active,
        Instant activatedAt,
        String activatedBy,
        Map<String, Object> metadata
    ) {
        private ModelVersionState withActive(boolean active) {
            return new ModelVersionState(version, active, activatedAt, activatedBy, metadata);
        }
    }
}
