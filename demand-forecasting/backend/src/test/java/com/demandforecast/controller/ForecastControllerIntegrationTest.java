package com.demandforecast.controller;

import com.demandforecast.dto.ForecastRequest;
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
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ForecastControllerIntegrationTest {

    private static WireMockServer wireMock;

    @Autowired TestRestTemplate restTemplate;

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
        stubFor(post(urlEqualTo("/predict")).willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"demand\": " + demand + ", \"status\": \"ok\", \"request_id\": \"test\"}")));
    }

    private void stubMlPredictBatch(List<Double> demands) {
        String items = demands.stream().map(d -> "{\"demand\": " + d + ", \"status\": \"ok\"}")
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
}
