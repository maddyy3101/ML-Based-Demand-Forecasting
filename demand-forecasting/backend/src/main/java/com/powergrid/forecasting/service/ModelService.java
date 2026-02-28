package com.powergrid.forecasting.service;

import com.powergrid.forecasting.client.MlApiClient;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ModelService {

    private final MlApiClient mlApiClient;
    private volatile String activeVersion = "XGBoost-Tuned";

    public ModelService(MlApiClient mlApiClient) {
        this.mlApiClient = mlApiClient;
    }

    public Map<String, Object> getActiveModel() {
        Map<String, Object> modelInfo = mlApiClient.getModelInfo();
        Map<String, Object> response = new HashMap<>(modelInfo);
        response.put("activeVersion", activeVersion);
        response.put("updatedAt", Instant.now().toString());
        return response;
    }

    public Map<String, Object> activate(String version, String reason) {
        this.activeVersion = version;
        return Map.of(
                "status", "activated",
                "activeVersion", version,
                "reason", reason == null ? "manual rollout" : reason,
                "activatedAt", Instant.now().toString()
        );
    }
}
