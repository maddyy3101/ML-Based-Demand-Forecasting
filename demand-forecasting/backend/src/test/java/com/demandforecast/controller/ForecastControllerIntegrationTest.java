package com.demandforecast.controller;

import com.demandforecast.dto.ForecastRequest;
import com.demandforecast.entity.PredictionRecord;
import com.demandforecast.repository.PredictionRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ForecastControllerIntegrationTest {

    private static WireMockServer wireMock;

    @Autowired TestRestTemplate restTemplate;
    @Autowired PredictionRepository predictionRepository;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().port(9090));
        wireMock.start();
        WireMock.configureFor("localhost", 9090);
    }

    @AfterAll
    static void stopWireMock() { wireMock.stop(); }

    @AfterEach
    void resetStubs() { wireMock.resetAll(); }

    private ForecastRequest validRequest() {
        return ForecastRequest.builder()
            .date(LocalDate.of(2025, 6, 15)).category("Electronics").region("North")
            .inventoryLevel(150).unitsSold(80).unitsOrdered(200).price(72.5).discount(10.0)
            .weatherCondition("Sunny").promotion(true).competitorPricing(75.0)
            .seasonality("Summer").epidemic(false).build();
    }

    private void stubMlPredict(double demand) {
        double lower = Math.round((demand - 12.5) * 100.0) / 100.0;
        double upper = Math.round((demand + 12.5) * 100.0) / 100.0;
        stubFor(post(urlEqualTo("/predict")).willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"demand\": " + demand + ", \"lower_bound\": " + lower +
                ", \"upper_bound\": " + upper + ", \"status\": \"ok\", \"request_id\": \"test\"}")));
    }

    private void stubMlPredictBatch(List<Double> demands) {
        String items = demands.stream().map(d -> {
            double lower = Math.round((d - 12.5) * 100.0) / 100.0;
            double upper = Math.round((d + 12.5) * 100.0) / 100.0;
            return "{\"demand\": " + d + ", \"lower_bound\": " + lower +
                ", \"upper_bound\": " + upper + ", \"status\": \"ok\"}";
        })
            .collect(Collectors.joining(",", "[", "]"));
        stubFor(post(urlEqualTo("/predict/batch")).willReturn(aResponse()
            .withHeader("Content-Type", "application/json").withBody(items)));
    }

    @Test
    void forecast_returnsCreatedWithDemand() {
        stubMlPredict(142.5);
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/forecasts", validRequest(), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Number) resp.getBody().get("demand")).doubleValue()).isEqualTo(142.5);
        assertThat(resp.getBody()).containsKeys("lowerBound", "upperBound");
        assertThat(resp.getBody()).containsKey("predictionId");
    }

    @Test
    void forecast_echoesRequestId() {
        stubMlPredict(100.0);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-ID", "my-trace-id");
        ResponseEntity<Map> resp = restTemplate.exchange("/api/v1/forecasts", HttpMethod.POST,
            new HttpEntity<>(validRequest(), headers), Map.class);
        assertThat(resp.getHeaders().getFirst("X-Request-ID")).isEqualTo("my-trace-id");
    }

    @Test
    void forecast_invalidCategory_returns422() {
        ForecastRequest bad = ForecastRequest.builder()
            .date(LocalDate.now()).category("Weapons").region("North")
            .inventoryLevel(10).unitsOrdered(10).price(10).discount(5)
            .weatherCondition("Sunny").competitorPricing(10).seasonality("Summer").build();
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/forecasts", bad, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody()).containsKey("fieldErrors");
    }

    @Test
    void forecast_mlApiDown_returns503() {
        stubFor(post(urlEqualTo("/predict")).willReturn(aResponse().withStatus(500).withBody("error")));
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/forecasts", validRequest(), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void forecastBatch_returnsList() {
        stubMlPredictBatch(List.of(100.0, 200.0, 300.0));
        ResponseEntity<List> resp = restTemplate.postForEntity("/api/v1/forecasts/batch",
            List.of(validRequest(), validRequest(), validRequest()), List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).hasSize(3);
    }

    @Test
    void mlHealth_returnsOkWhenFlaskUp() {
        stubFor(get(urlEqualTo("/health")).willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"status\":\"ok\",\"model_type\":\"XGBRegressor\"}")));
        ResponseEntity<Map> resp = restTemplate.getForEntity("/api/v1/ml/health", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("mlApi")).isEqualTo("UP");
    }

    @Test
    void modelInfo_returnsProxyPayload() {
        stubFor(get(urlEqualTo("/model/info")).willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"model_type\":\"XGBRegressor\",\"n_features\":17}")));

        ResponseEntity<Map> resp = restTemplate.getForEntity("/api/v1/ml/model-info", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("model_type")).isEqualTo("XGBRegressor");
        assertThat(((Number) resp.getBody().get("n_features")).intValue()).isEqualTo(17);
    }

    @Test
    void accuracy_returnsComputedMetrics() {
        stubMlPredict(120.0);
        ResponseEntity<Map> created = restTemplate.postForEntity("/api/v1/forecasts", validRequest(), Map.class);
        String predictionId = (String) created.getBody().get("predictionId");
        PredictionRecord saved = predictionRepository.findById(java.util.UUID.fromString(predictionId)).orElseThrow();
        saved.setActualDemand(100.0);
        predictionRepository.save(saved);

        ResponseEntity<Map> resp = restTemplate.getForEntity(
            "/api/v1/forecasts/accuracy?category=Electronics&region=North", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) resp.getBody().get("sampleCount")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) resp.getBody().get("mae")).doubleValue()).isGreaterThan(0.0);
        assertThat(((Number) resp.getBody().get("rmse")).doubleValue()).isGreaterThan(0.0);
    }

    @Test
    void forecastAsync_createsJobAndProvidesStatus() {
        stubMlPredictBatch(List.of(100.0, 200.0));

        ResponseEntity<Map> created = restTemplate.postForEntity(
            "/api/v1/forecasts/async", List.of(validRequest(), validRequest()), Map.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String jobId = (String) created.getBody().get("jobId");
        assertThat(jobId).isNotBlank();

        ResponseEntity<Map> job = restTemplate.getForEntity("/api/v1/jobs/" + jobId, Map.class);
        assertThat(job.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(job.getBody()).containsKeys("status", "jobType");
    }

    @Test
    void modelsActiveAndActivate_work() {
        stubFor(get(urlEqualTo("/model/info")).willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"model_type\":\"XGBRegressor\",\"n_features\":17}")));

        ResponseEntity<Map> active = restTemplate.getForEntity("/api/v1/models/active", Map.class);
        assertThat(active.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(active.getBody().get("version")).isEqualTo("v1");

        ResponseEntity<Map> activated = restTemplate.postForEntity(
            "/api/v1/models/v2/activate",
            Map.of("activatedBy", "test-user", "metadata", Map.of("reason", "rollback-test")),
            Map.class
        );
        assertThat(activated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activated.getBody().get("version")).isEqualTo("v2");
    }

    @Test
    void whatIf_returnsScenarios() {
        stubMlPredict(120.0);

        Map<String, Object> body = Map.of(
            "base", Map.ofEntries(
                Map.entry("date", "2025-06-15"),
                Map.entry("category", "Electronics"),
                Map.entry("region", "North"),
                Map.entry("inventoryLevel", 150),
                Map.entry("unitsSold", 80),
                Map.entry("unitsOrdered", 200),
                Map.entry("price", 72.5),
                Map.entry("discount", 10.0),
                Map.entry("weatherCondition", "Sunny"),
                Map.entry("promotion", true),
                Map.entry("competitorPricing", 75.0),
                Map.entry("seasonality", "Summer"),
                Map.entry("epidemic", false)
            ),
            "scenarios", List.of(
                Map.of("name", "lower-price", "price", 68.0),
                Map.of("name", "high-discount", "discount", 20.0)
            )
        );

        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/forecasts/what-if", body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKeys("baseDemand", "scenarios");
        assertThat((List<?>) resp.getBody().get("scenarios")).hasSize(2);
    }

    @Test
    void explanationAndBacktestEndpoints_work() {
        stubMlPredict(118.0);
        ResponseEntity<Map> created = restTemplate.postForEntity("/api/v1/forecasts", validRequest(), Map.class);
        String predictionId = (String) created.getBody().get("predictionId");

        PredictionRecord saved = predictionRepository.findById(UUID.fromString(predictionId)).orElseThrow();
        saved.setActualDemand(115.0);
        predictionRepository.save(saved);

        ResponseEntity<Map> explanation = restTemplate.getForEntity(
            "/api/v1/forecasts/" + predictionId + "/explanation", Map.class);
        assertThat(explanation.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(explanation.getBody()).containsKey("contributions");

        ResponseEntity<Map> backtest = restTemplate.postForEntity(
            "/api/v1/backtests",
            Map.of("fromDate", "2025-06-01", "toDate", "2025-06-30", "category", "Electronics", "region", "North"),
            Map.class
        );
        assertThat(backtest.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(backtest.getBody()).containsKey("jobId");
    }
}
