package com.demandforecast.client;

import com.demandforecast.exception.MlApiException;
import com.demandforecast.exception.MlApiUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MlApiClient {

    @Value("${ml.api.base-url}")
    private String baseUrl;

    @Value("${ml.api.timeout-seconds:10}")
    private int timeoutSeconds;

    private WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    void init() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
            .doOnConnected(conn -> conn.addHandlerLast(
                new ReadTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS)));
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader("Content-Type", "application/json")
            .build();
        log.info("MlApiClient initialised → {}", baseUrl);
    }

    public Mono<MlPredictResult> predict(MlPredictPayload payload, String requestId) {
        return webClient.post().uri("/predict")
            .header("X-Request-ID", requestId)
            .bodyValue(buildBody(payload))
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, resp ->
                resp.bodyToMono(String.class).map(b -> new MlApiException("ML API rejected request (4xx): " + b)))
            .onStatus(HttpStatusCode::is5xxServerError, resp ->
                resp.bodyToMono(String.class).map(b -> new MlApiUnavailableException(new RuntimeException(b))))
            .bodyToMono(JsonNode.class)
            .map(this::toPredictResult)
            .retryWhen(Retry.backoff(2, Duration.ofMillis(300))
                .filter(ex -> ex instanceof WebClientRequestException)
                .onRetryExhaustedThrow((spec, sig) -> new MlApiUnavailableException(sig.failure())))
            .onErrorMap(WebClientRequestException.class, MlApiUnavailableException::new);
    }

    public Mono<List<MlPredictResult>> predictBatch(List<MlPredictPayload> payloads, String requestId) {
        return webClient.post().uri("/predict/batch")
            .header("X-Request-ID", requestId)
            .bodyValue(payloads.stream().map(this::buildBody).toList())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, resp ->
                resp.bodyToMono(String.class).map(b -> new MlApiException("ML API rejected batch request (4xx): " + b)))
            .onStatus(HttpStatusCode::is5xxServerError, resp ->
                resp.bodyToMono(String.class).map(b -> new MlApiUnavailableException(new RuntimeException(b))))
            .bodyToFlux(JsonNode.class)
            .map(this::toPredictResult)
            .collectList();
    }

    public Mono<Boolean> isHealthy() {
        return webClient.get().uri("/health").retrieve()
            .bodyToMono(JsonNode.class)
            .map(json -> "ok".equals(json.get("status").asText()))
            .onErrorReturn(false);
    }

    public Mono<Map<String, Double>> getFeatureImportance() {
        return webClient.get().uri("/feature-importance").retrieve()
            .bodyToFlux(JsonNode.class)
            .collectMap(n -> n.get("feature").asText(), n -> n.get("importance").asDouble());
    }

    public Mono<Map<String, Object>> getModelInfo() {
        return webClient.get().uri("/model/info").retrieve()
            .bodyToMono(JsonNode.class)
            .map(json -> mapper.convertValue(json, Map.class));
    }

    private MlPredictResult toPredictResult(JsonNode json) {
        if (json == null || !json.hasNonNull("demand")) {
            throw new MlApiException("ML API response missing 'demand': " + String.valueOf(json));
        }
        double demand = json.get("demand").asDouble();
        Double lower = readOptionalDouble(json, "lower_bound");
        Double upper = readOptionalDouble(json, "upper_bound");
        return new MlPredictResult(demand, lower, upper);
    }

    private Double readOptionalDouble(JsonNode json, String key) {
        JsonNode node = json.get(key);
        return (node == null || node.isNull()) ? null : node.asDouble();
    }

    private ObjectNode buildBody(MlPredictPayload p) {
        ObjectNode node = mapper.createObjectNode();
        node.put("date",               p.date().format(DateTimeFormatter.ISO_DATE));
        node.put("category",           p.category());
        node.put("region",             p.region());
        node.put("inventory_level",    p.inventoryLevel());
        node.put("units_sold",         p.unitsSold());
        node.put("units_ordered",      p.unitsOrdered());
        node.put("price",              p.price());
        node.put("discount",           p.discount());
        node.put("weather_condition",  p.weatherCondition());
        node.put("promotion",          p.promotion() ? 1 : 0);
        node.put("competitor_pricing", p.competitorPricing());
        node.put("seasonality",        p.seasonality());
        node.put("epidemic",           p.epidemic() ? 1 : 0);
        return node;
    }

    public record MlPredictPayload(
        LocalDate date, String category, String region,
        int inventoryLevel, int unitsSold, int unitsOrdered,
        double price, double discount, String weatherCondition,
        boolean promotion, double competitorPricing,
        String seasonality, boolean epidemic
    ) {}

    public record MlPredictResult(double demand, Double lowerBound, Double upperBound) {
        public MlPredictResult {
            if (Objects.nonNull(lowerBound) && Objects.nonNull(upperBound) && lowerBound > upperBound) {
                double temp = lowerBound;
                lowerBound = upperBound;
                upperBound = temp;
            }
        }
    }
}
