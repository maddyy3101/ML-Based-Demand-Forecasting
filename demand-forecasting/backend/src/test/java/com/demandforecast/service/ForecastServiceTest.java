package com.demandforecast.service;

import com.demandforecast.client.MlApiClient;
import com.demandforecast.dto.ForecastRequest;
import com.demandforecast.dto.ForecastResponse;
import com.demandforecast.entity.PredictionRecord;
import com.demandforecast.exception.BatchSizeExceededException;
import com.demandforecast.exception.PredictionNotFoundException;
import com.demandforecast.repository.PredictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock MlApiClient          mlApiClient;
    @Mock PredictionRepository repository;
    @InjectMocks ForecastService service;

    private ForecastRequest validRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxBatchSize", 256);
        validRequest = ForecastRequest.builder()
            .date(LocalDate.of(2025, 6, 15)).category("Electronics").region("North")
            .inventoryLevel(150).unitsSold(80).unitsOrdered(200).price(72.5).discount(10.0)
            .weatherCondition("Sunny").promotion(true).competitorPricing(75.0)
            .seasonality("Summer").epidemic(false).build();
    }

    private PredictionRecord savedRecord(double demand) {
        return PredictionRecord.builder().id(UUID.randomUUID())
            .forecastDate(validRequest.getDate()).category(validRequest.getCategory())
            .region(validRequest.getRegion()).predictedDemand(demand)
            .lowerBound(demand - 12.5).upperBound(demand + 12.5)
            .createdAt(Instant.now()).requestId("req-123").build();
    }

    @Test
    void forecast_callsMlApiAndPersists() {
        PredictionRecord record = savedRecord(142.5);
        when(mlApiClient.predict(any(), anyString()))
            .thenReturn(Mono.just(new MlApiClient.MlPredictResult(142.5, 130.0, 155.0)));
        when(repository.save(any())).thenReturn(record);
        StepVerifier.create(service.forecast(validRequest, "req-123"))
            .assertNext(resp -> {
                assertThat(resp.getDemand()).isEqualTo(142.5);
                assertThat(resp.getCategory()).isEqualTo("Electronics");
                assertThat(resp.getPredictionId()).isNotNull();
                assertThat(resp.getLowerBound()).isEqualTo(130.0);
                assertThat(resp.getUpperBound()).isEqualTo(155.0);
            }).verifyComplete();
        verify(mlApiClient, times(1)).predict(any(), eq("req-123"));
        verify(repository, times(1)).save(any());
    }

    @Test
    void forecast_mlApiError_propagatesException() {
        when(mlApiClient.predict(any(), anyString())).thenReturn(Mono.error(new RuntimeException("ML down")));
        StepVerifier.create(service.forecast(validRequest, "req-1"))
            .expectErrorMessage("ML down").verify();
        verify(repository, never()).save(any());
    }

    @Test
    void forecastBatch_returnsCorrectCount() {
        when(mlApiClient.predictBatch(anyList(), anyString())).thenReturn(Mono.just(List.of(
            new MlApiClient.MlPredictResult(100.0, 90.0, 110.0),
            new MlApiClient.MlPredictResult(200.0, 180.0, 220.0)
        )));
        when(repository.save(any())).thenAnswer(inv -> {
            PredictionRecord r = inv.getArgument(0);
            return PredictionRecord.builder().id(UUID.randomUUID())
                .forecastDate(LocalDate.now()).category("Electronics").region("North")
                .predictedDemand(r.getPredictedDemand()).createdAt(Instant.now()).requestId("b1").build();
        });
        StepVerifier.create(service.forecastBatch(List.of(validRequest, validRequest), "b1"))
            .assertNext(list -> assertThat(list).hasSize(2)).verifyComplete();
    }

    @Test
    void forecastBatch_exceedsMaxSize_throwsImmediately() {
        assertThatThrownBy(() -> service.forecastBatch(Collections.nCopies(300, validRequest), "x"))
            .isInstanceOf(BatchSizeExceededException.class);
        verifyNoInteractions(mlApiClient);
    }

    @Test
    void recordActualDemand_updatesRecord() {
        UUID id = UUID.randomUUID();
        PredictionRecord record = savedRecord(100.0);
        record.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(record));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.recordActualDemand(id, 95.0);
        assertThat(record.getActualDemand()).isEqualTo(95.0);
        verify(repository).save(record);
    }

    @Test
    void recordActualDemand_notFound_throwsPredictionNotFoundException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordActualDemand(id, 50.0))
            .isInstanceOf(PredictionNotFoundException.class)
            .hasMessageContaining(id.toString());
    }

    @Test
    void getAccuracyMetrics_emptyData_returnsZeroSample() {
        when(repository.findAccuracyRecords(any(), any(), any(), any())).thenReturn(List.of());

        var resp = service.getAccuracyMetrics("Electronics", "North",
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertThat(resp.getSampleCount()).isZero();
        assertThat(resp.getMae()).isNull();
        assertThat(resp.getRmse()).isNull();
        assertThat(resp.getMape()).isNull();
    }

    @Test
    void getAccuracyMetrics_computesMaeRmseMape() {
        PredictionRecord r1 = PredictionRecord.builder()
            .predictedDemand(110.0).actualDemand(100.0).build();
        PredictionRecord r2 = PredictionRecord.builder()
            .predictedDemand(90.0).actualDemand(100.0).build();
        when(repository.findAccuracyRecords(any(), any(), any(), any())).thenReturn(List.of(r1, r2));

        var resp = service.getAccuracyMetrics(null, null, null, null);

        assertThat(resp.getSampleCount()).isEqualTo(2);
        assertThat(resp.getMae()).isEqualTo(10.0);
        assertThat(resp.getRmse()).isEqualTo(10.0);
        assertThat(resp.getMape()).isEqualTo(10.0);
    }
}
