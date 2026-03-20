package com.powergrid.forecasting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergrid.forecasting.dto.ChatMessageDto;
import com.powergrid.forecasting.dto.admin.ProcDocStatusDto;
import com.powergrid.forecasting.entity.MaterialInventory;
import com.powergrid.forecasting.entity.ProcurementForecast;
import com.powergrid.forecasting.entity.RecommendationAction;
import com.powergrid.forecasting.exception.ValidationFailureException;
import com.powergrid.forecasting.repository.MaterialInventoryRepository;
import com.powergrid.forecasting.repository.ProcurementForecastRepository;
import com.powergrid.forecasting.repository.RecommendationActionRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {

    private enum Provider {
        GROQ,
        GEMINI
    }

    private final ProcurementForecastRepository forecastRepository;
    private final MaterialInventoryRepository inventoryRepository;
    private final RecommendationActionRepository recommendationRepository;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;
    private final WebClient webClient = WebClient.create();

    @Value("${groq.api.key:}")
    private String groqApiKeyFromConfig;

    @Value("${groq.api.model:llama-3.1-8b-instant}")
    private String groqModel;

    @Value("${gemini.api.key:}")
    private String geminiApiKeyFromConfig;

    @Value("${gemini.api.model:gemini-2.0-flash}")
    private String geminiModel;

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GEMINI_URL_PREFIX = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String GEMINI_URL_SUFFIX = ":generateContent?key=";

    private volatile String runtimeApiKey;

    public Mono<String> getChatbotResponse(String userMessage, List<ChatMessageDto> history) {
        String safeMessage = userMessage == null ? "" : userMessage.trim();
        List<ChatMessageDto> safeHistory = history == null ? List.of() : history;

        String effectiveApiKey = resolveApiKey();
        if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
            return Mono.just(buildFallbackResponse(safeMessage, "No LLM API key is configured."));
        }
        Provider provider = resolveProvider(effectiveApiKey);

        return Mono.fromCallable(this::buildSystemPrompt)
                .flatMap(systemPrompt -> {
                    Mono<String> llmCall = provider == Provider.GROQ
                            ? callGroq(systemPrompt, safeMessage, safeHistory, effectiveApiKey)
                            : callGemini(systemPrompt, safeMessage, safeHistory, effectiveApiKey);

                    return llmCall.onErrorResume(error -> {
                        String reason = summarizeProviderFailure(provider, error);
                        log.error("ProcBot provider failure [{}]: {}", provider, reason, error);
                        return Mono.just(buildFallbackResponse(safeMessage, reason));
                    });
                });
    }

    public synchronized ProcDocStatusDto authenticateAndUseApiKey(String apiKey) {
        String normalized = apiKey == null ? "" : apiKey.trim();
        if (normalized.isBlank()) {
            throw new ValidationFailureException("API key is required.");
        }

        Provider provider = detectProviderByKey(normalized);
        if (provider == null) {
            throw new ValidationFailureException("Unsupported API key format. Use gsk_... (Groq) or AIza... (Gemini).");
        }

        validateApiKey(provider, normalized);
        runtimeApiKey = normalized;
        return buildProcDocStatus("ProcDoc key authenticated and activated (" + provider.name() + ").");
    }

    public ProcDocStatusDto getProcDocStatus() {
        return buildProcDocStatus("ProcDoc key status loaded.");
    }

    private ProcDocStatusDto buildProcDocStatus(String message) {
        String key = resolveApiKey();
        Provider provider = resolveProvider(key);
        String source = resolveSource();
        return new ProcDocStatusDto(
                key != null && !key.isBlank(),
                source,
                maskKey(key),
                message + " Active provider: " + provider.name()
        );
    }

    private String buildSystemPrompt() {
        String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));

        List<ProcurementForecast> forecasts = forecastRepository.findAll();
        List<Map<String, Object>> forecastSummary = forecasts.stream().limit(10).map(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("material", f.getMaterialType());
            m.put("predicted", f.getPredictedQuantity());
            m.put("confidence", f.getModelConfidence());
            m.put("region", f.getRegion());
            return m;
        }).collect(Collectors.toList());

        List<MaterialInventory> inventory = inventoryRepository.findAll();
        List<Map<String, Object>> stockoutRisks = inventory.stream()
                .filter(i -> i.getCurrentStock() <= i.getReorderThreshold())
                .map(i -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("material", i.getMaterialName());
                    m.put("currentStock", i.getCurrentStock());
                    m.put("reorderThreshold", i.getReorderThreshold());
                    return m;
                }).collect(Collectors.toList());

        List<RecommendationAction> actions = recommendationRepository.findAll();
        Map<String, Object> drift = metricsService.driftSummary();

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are ProcBot AI, an intelligent procurement assistant for POWERGRID's demand forecasting system.\n");
        prompt.append("Your goal is to help users understand forecasted data, highlight risks, and give procurement recommendations.\n");
        prompt.append("Current Date: ").append(currentDate).append("\n\n");

        prompt.append("--- SYSTEM DATA CONTEXT ---\n");
        try {
            prompt.append("Active Forecasts (Sample): ").append(objectMapper.writeValueAsString(forecastSummary)).append("\n");
            prompt.append("Stockout Risks: ").append(objectMapper.writeValueAsString(stockoutRisks)).append("\n");
            prompt.append("Pending Recommendation Actions: ").append(actions.size()).append("\n");
            prompt.append("Drift: ").append(objectMapper.writeValueAsString(drift)).append("\n");
        } catch (JsonProcessingException e) {
            prompt.append("Data context unavailable.\n");
        }
        prompt.append("--- END DATA CONTEXT ---\n\n");

        prompt.append("Instructions:\n");
        prompt.append("1. Be concise but helpful. Always use plain English.\n");
        prompt.append("2. Use the provided data context to answer accurately. If data is missing, admit it.\n");
        prompt.append("3. Reference Indian power sector terms (Transmission Towers, Conductors).\n");

        return prompt.toString();
    }

    private Mono<String> callGroq(String systemPrompt, String userMessage, List<ChatMessageDto> history, String apiKey) {
        Map<String, Object> requestBody = buildGroqRequestBody(systemPrompt, userMessage, history);
        return webClient
                .post()
                .uri(GROQ_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractGroqResponseContent);
    }

    private Mono<String> callGemini(String systemPrompt, String userMessage, List<ChatMessageDto> history, String apiKey) {
        Map<String, Object> requestBody = buildGeminiRequestBody(systemPrompt, userMessage, history);
        String url = GEMINI_URL_PREFIX + geminiModel + GEMINI_URL_SUFFIX + apiKey;
        return webClient
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractGeminiResponseContent);
    }

    private Map<String, Object> buildGroqRequestBody(String systemPrompt, String userMessage, List<ChatMessageDto> history) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", groqModel);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        for (ChatMessageDto msg : history == null ? List.<ChatMessageDto>of() : history) {
            messages.add(Map.of(
                    "role", normalizeGroqRole(msg == null ? null : msg.getRole()),
                    "content", msg != null && msg.getContent() != null ? msg.getContent() : ""
            ));
        }

        messages.add(Map.of("role", "user", "content", userMessage == null ? "" : userMessage));
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("max_tokens", 1024);

        return body;
    }

    private Map<String, Object> buildGeminiRequestBody(String systemPrompt, String userMessage, List<ChatMessageDto> history) {
        Map<String, Object> body = new HashMap<>();

        Map<String, Object> systemInstruction = new HashMap<>();
        systemInstruction.put("parts", List.of(Map.of("text", systemPrompt)));
        body.put("system_instruction", systemInstruction);

        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatMessageDto msg : history == null ? List.<ChatMessageDto>of() : history) {
            String role = normalizeGeminiRole(msg == null ? null : msg.getRole());
            String content = msg != null && msg.getContent() != null ? msg.getContent() : "";
            contents.add(Map.of(
                    "role", role,
                    "parts", List.of(Map.of("text", content))
            ));
        }

        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userMessage == null ? "" : userMessage))
        ));
        body.put("contents", contents);
        body.put("generationConfig", Map.of(
                "temperature", 0.7,
                "topP", 0.95,
                "topK", 40,
                "maxOutputTokens", 1024
        ));
        return body;
    }

    private String extractGroqResponseContent(Map response) {
        try {
            List<Map> choices = (List<Map>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map choice = choices.get(0);
                Map message = (Map) choice.get("message");
                if (message != null) {
                    return (String) message.get("content");
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Groq response: {}", e.getMessage());
        }
        throw new IllegalStateException("Unable to parse Groq response payload.");
    }

    private String extractGeminiResponseContent(Map response) {
        try {
            List<Map> candidates = (List<Map>) response.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map candidate = candidates.get(0);
                Map content = (Map) candidate.get("content");
                List<Map> parts = content == null ? null : (List<Map>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    return Objects.toString(parts.get(0).get("text"), "");
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
        }
        throw new IllegalStateException("Unable to parse Gemini response payload.");
    }

    public List<String> generateSuggestedQuestions(String lastResponse) {
        return List.of(
                "Which materials are at stockout risk?",
                "What does the drift score mean?",
                "Show me the replenishment plan summary"
        );
    }

    private Provider resolveProvider(String key) {
        Provider detected = detectProviderByKey(key);
        if (detected != null) {
            return detected;
        }
        if (groqApiKeyFromConfig != null && !groqApiKeyFromConfig.isBlank()) {
            return Provider.GROQ;
        }
        if (geminiApiKeyFromConfig != null && !geminiApiKeyFromConfig.isBlank()) {
            return Provider.GEMINI;
        }
        return Provider.GROQ;
    }

    private String resolveApiKey() {
        if (runtimeApiKey != null && !runtimeApiKey.isBlank()) {
            return runtimeApiKey;
        }
        if (groqApiKeyFromConfig != null && !groqApiKeyFromConfig.isBlank()) {
            return groqApiKeyFromConfig;
        }
        if (geminiApiKeyFromConfig != null && !geminiApiKeyFromConfig.isBlank()) {
            return geminiApiKeyFromConfig;
        }
        return "";
    }

    private String resolveSource() {
        if (runtimeApiKey != null && !runtimeApiKey.isBlank()) {
            return "ADMIN_OVERRIDE";
        }
        if ((groqApiKeyFromConfig != null && !groqApiKeyFromConfig.isBlank())
                || (geminiApiKeyFromConfig != null && !geminiApiKeyFromConfig.isBlank())) {
            return "APPLICATION_CONFIG";
        }
        return "NOT_CONFIGURED";
    }

    private Provider detectProviderByKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.startsWith("gsk_")) {
            return Provider.GROQ;
        }
        if (key.startsWith("AIza")) {
            return Provider.GEMINI;
        }
        return null;
    }

    private void validateApiKey(Provider provider, String key) {
        try {
            if (provider == Provider.GROQ) {
                Map<String, Object> body = Map.of(
                        "model", groqModel,
                        "messages", List.of(Map.of("role", "user", "content", "Reply with exactly OK")),
                        "max_tokens", 8
                );
                Map response = webClient
                        .post()
                        .uri(GROQ_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(20));
                if (response == null || !response.containsKey("choices")) {
                    throw new ValidationFailureException("Groq API authentication failed. Empty response received.");
                }
            } else {
                Map<String, Object> body = buildGeminiRequestBody(
                        "You are ProcBot AI, an intelligent procurement assistant for POWERGRID's demand forecasting system.",
                        "Reply with exactly OK",
                        List.of()
                );
                String url = GEMINI_URL_PREFIX + geminiModel + GEMINI_URL_SUFFIX + key;
                Map response = webClient
                        .post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(20));
                if (response == null || !response.containsKey("candidates")) {
                    throw new ValidationFailureException("Gemini API authentication failed. Empty response received.");
                }
            }
        } catch (WebClientResponseException ex) {
            throw new ValidationFailureException(provider.name() + " API authentication failed (HTTP "
                    + ex.getStatusCode().value() + ").");
        } catch (ValidationFailureException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ValidationFailureException(
                    provider.name() + " API authentication failed: " + Objects.toString(ex.getMessage(), "Unknown error")
            );
        }
    }

    private String normalizeGroqRole(String role) {
        String value = role == null ? "" : role.toLowerCase(Locale.ROOT);
        if ("assistant".equals(value) || "model".equals(value)) {
            return "assistant";
        }
        if ("system".equals(value)) {
            return "system";
        }
        return "user";
    }

    private String normalizeGeminiRole(String role) {
        String value = role == null ? "" : role.toLowerCase(Locale.ROOT);
        if ("assistant".equals(value) || "model".equals(value)) {
            return "model";
        }
        return "user";
    }

    private String summarizeProviderFailure(Provider provider, Throwable error) {
        if (error instanceof WebClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            if (status == 401 || status == 403) {
                return provider.name() + " API key is invalid or unauthorized (HTTP " + status + ").";
            }
            if (status == 429) {
                return provider.name() + " API quota/rate limit reached (HTTP 429).";
            }
            if (status >= 500) {
                return provider.name() + " API is temporarily unavailable (HTTP " + status + ").";
            }
            return provider.name() + " API request failed (HTTP " + status + ").";
        }
        String message = Objects.toString(error.getMessage(), "Unknown error");
        if (message.toLowerCase(Locale.ROOT).contains("timed out")) {
            return provider.name() + " API request timed out.";
        }
        return provider.name() + " API error: " + message;
    }

    private String buildFallbackResponse(String userMessage, String reason) {
        String query = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        if (query.contains("stockout") || query.contains("risk")) {
            return buildStockoutRiskFallback(reason);
        }
        if (query.contains("drift")) {
            return buildDriftFallback(reason);
        }
        if (query.contains("replenishment") || query.contains("purchase") || query.contains("procure")
                || query.contains("plan")) {
            return buildReplenishmentFallback(reason);
        }
        return buildForecastSummaryFallback(reason);
    }

    private String buildStockoutRiskFallback(String reason) {
        List<MaterialInventory> risks = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getCurrentStock() <= inv.getReorderThreshold())
                .sorted(Comparator.comparingDouble(inv -> inv.getReorderThreshold() == 0
                        ? 1.0
                        : (double) inv.getCurrentStock() / inv.getReorderThreshold()))
                .limit(5)
                .toList();

        if (risks.isEmpty()) {
            return "ProcBot fallback mode (" + reason + ")\nNo material is currently below reorder threshold.";
        }

        StringBuilder text = new StringBuilder();
        text.append("ProcBot fallback mode (").append(reason).append(")\nCurrent stockout/low-stock risks:\n");
        for (int i = 0; i < risks.size(); i++) {
            MaterialInventory inv = risks.get(i);
            text.append(i + 1).append(") ")
                    .append(inv.getMaterialType().getDisplayName())
                    .append(" | ").append(inv.getRegion().getDisplayName())
                    .append(" | current ").append(inv.getCurrentStock()).append(" ").append(inv.getUnitLabel())
                    .append(" vs threshold ").append(inv.getReorderThreshold())
                    .append(" (SKU ").append(inv.getSku()).append(")\n");
        }
        return text.toString().trim();
    }

    private String buildDriftFallback(String reason) {
        Map<String, Object> drift = metricsService.driftSummary();
        double featureDrift = ((Number) drift.getOrDefault("feature_drift_percent", 0.0)).doubleValue();
        double predictionDrift = ((Number) drift.getOrDefault("prediction_drift_percent", 0.0)).doubleValue();

        return "ProcBot fallback mode (" + reason + ")\n"
                + "Drift snapshot: feature drift " + round(featureDrift) + "%, prediction drift "
                + round(predictionDrift) + "%.\n"
                + "Sample sizes: recent=" + drift.getOrDefault("recent_window_size", 0)
                + ", previous=" + drift.getOrDefault("previous_window_size", 0) + ".";
    }

    private String buildReplenishmentFallback(String reason) {
        List<MaterialInventory> inventoryItems = inventoryRepository.findAll();
        if (inventoryItems.isEmpty()) {
            return "ProcBot fallback mode (" + reason + ")\nNo inventory records are available.";
        }

        record Line(String material, String region, String unit, int current, int predicted, int recommended) {}
        List<Line> lines = new ArrayList<>();
        for (MaterialInventory inv : inventoryItems) {
            ProcurementForecast latest = forecastRepository
                    .findTopByMaterialTypeAndRegionOrderByCreatedAtDesc(inv.getMaterialType(), inv.getRegion())
                    .orElse(null);
            int predicted = latest != null ? latest.getPredictedQuantity() : inv.getReorderThreshold();
            int recommended = Math.max(0, (int) Math.ceil((predicted * 1.5) - inv.getCurrentStock()));
            lines.add(new Line(
                    inv.getMaterialType().getDisplayName(),
                    inv.getRegion().getDisplayName(),
                    inv.getUnitLabel(),
                    inv.getCurrentStock(),
                    predicted,
                    recommended
            ));
        }

        List<Line> top = lines.stream()
                .filter(line -> line.recommended() > 0)
                .sorted(Comparator.comparingInt(Line::recommended).reversed())
                .limit(5)
                .toList();

        if (top.isEmpty()) {
            return "ProcBot fallback mode (" + reason + ")\nNo urgent replenishment is required right now.";
        }

        StringBuilder text = new StringBuilder();
        text.append("ProcBot fallback mode (").append(reason).append(")\nTop replenishment priorities:\n");
        for (int i = 0; i < top.size(); i++) {
            Line line = top.get(i);
            text.append(i + 1).append(") ")
                    .append(line.material()).append(" | ").append(line.region())
                    .append(" | predicted ").append(line.predicted()).append(" ").append(line.unit())
                    .append(" | current ").append(line.current())
                    .append(" | recommended order ").append(line.recommended()).append(" ").append(line.unit())
                    .append("\n");
        }
        return text.toString().trim();
    }

    private String buildForecastSummaryFallback(String reason) {
        List<ProcurementForecast> forecasts = forecastRepository.findAll();
        if (forecasts.isEmpty()) {
            return "ProcBot fallback mode (" + reason + ")\nNo forecast records are available yet.";
        }

        int totalPredicted = forecasts.stream().mapToInt(ProcurementForecast::getPredictedQuantity).sum();
        double avgConfidence = forecasts.stream().mapToDouble(ProcurementForecast::getModelConfidence).average().orElse(0.0);

        Map<String, Integer> byMaterial = new HashMap<>();
        for (ProcurementForecast forecast : forecasts) {
            String material = forecast.getMaterialType().getDisplayName();
            byMaterial.put(material, byMaterial.getOrDefault(material, 0) + forecast.getPredictedQuantity());
        }

        List<Map.Entry<String, Integer>> topMaterials = byMaterial.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .toList();

        String topSummary = topMaterials.stream()
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .collect(Collectors.joining(", "));

        return "ProcBot fallback mode (" + reason + ")\n"
                + "Forecast summary: " + forecasts.size() + " records, total predicted demand "
                + totalPredicted + " units, average model confidence " + round(avgConfidence) + ".\n"
                + "Top materials by predicted demand: " + topSummary + ".";
    }

    private String maskKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "********";
        }
        return apiKey.substring(0, 4) + "********" + apiKey.substring(apiKey.length() - 4);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
