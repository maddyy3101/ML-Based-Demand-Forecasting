package com.powergrid.forecasting.client;

import com.powergrid.forecasting.dto.forecast.ForecastRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class MlApiClient {

    private final WebClient webClient;

    public MlApiClient(
            @Value("${ml.api.base-url}") String baseUrl,
            @Value("${ml.api.timeout-seconds:20}") long timeoutSeconds
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                .build();
    }

    public Map<String, Object> predict(ForecastRequest request) {
        return webClient.post()
                .uri("/predict")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toMlPayload(request))
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(30));
    }

    public Map<String, Object> predictBatch(List<ForecastRequest> requests) {
        List<Map<String, Object>> mapped = requests.stream().map(this::toMlPayload).toList();
        Map<String, Object> payload = new HashMap<>();
        payload.put("requests", mapped);
        return webClient.post()
                .uri("/predict/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(60));
    }

    public Map<String, Object> getHealth() {
        return webClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(10));
    }

    public Map<String, Object> getModelInfo() {
        return webClient.get()
                .uri("/model-info")
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(20));
    }

    public Map<String, Object> getFeatureImportance() {
        return webClient.get()
                .uri("/feature-importance")
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(20));
    }

    public Map<String, Object> getAccuracy() {
        return webClient.get()
                .uri("/accuracy")
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(20));
    }

    public Map<String, Object> retrain(String datasetPath) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("datasetPath", datasetPath);
        return webClient.post()
                .uri("/retrain")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(10));
    }

    public Map<String, Object> whatIf(ForecastRequest request) {
        return predict(request);
    }

    private Map<String, Object> toMlPayload(ForecastRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("Project_Phase", request.projectPhase());
        payload.put("State", request.state());
        payload.put("Region", request.region());
        payload.put("Terrain_Type", request.terrainType());
        payload.put("Tower_Type", request.towerType());
        payload.put("Substation_Type", request.substationType());
        payload.put("Transmission_Length_KM", request.transmissionLengthKm());
        payload.put("Budget_Crore", request.budgetCrore());
        payload.put("Lead_Time_Days", request.leadTimeDays());
        payload.put("Tax_Percentage", request.taxPercentage());
        payload.put("Transportation_Cost", request.transportationCost());
        payload.put("Historical_Consumption", request.historicalConsumption());
        payload.put("Month", request.forecastMonth());
        payload.put("Year", request.forecastYear());
        payload.put("Material_Type", request.materialType());
        return payload;
    }
}
