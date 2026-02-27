package com.demandforecast.repository;

import com.demandforecast.entity.PredictionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PredictionRepository extends JpaRepository<PredictionRecord, UUID> {

    Page<PredictionRecord> findByCategoryAndRegionOrderByCreatedAtDesc(
        String category, String region, Pageable pageable);

    List<PredictionRecord> findByForecastDateBetweenOrderByForecastDateAsc(
        LocalDate from, LocalDate to);

    @Query("""
        SELECT p FROM PredictionRecord p
        WHERE p.actualDemand IS NOT NULL
          AND p.forecastDate BETWEEN :from AND :to
        ORDER BY p.forecastDate ASC
    """)
    List<PredictionRecord> findEvaluationRecords(
        @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
        SELECT p FROM PredictionRecord p
        WHERE p.actualDemand IS NOT NULL
          AND (:category IS NULL OR p.category = :category)
          AND (:region IS NULL OR p.region = :region)
          AND (:fromDate IS NULL OR p.forecastDate >= :fromDate)
          AND (:toDate IS NULL OR p.forecastDate <= :toDate)
        ORDER BY p.forecastDate ASC
    """)
    List<PredictionRecord> findAccuracyRecords(
        @Param("category") String category,
        @Param("region") String region,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate);

    @Query("""
        SELECT p.category, AVG(ABS(p.predictedDemand - p.actualDemand)) AS mae
        FROM PredictionRecord p
        WHERE p.actualDemand IS NOT NULL
        GROUP BY p.category
    """)
    List<Object[]> averageMaeByCategory();
}
