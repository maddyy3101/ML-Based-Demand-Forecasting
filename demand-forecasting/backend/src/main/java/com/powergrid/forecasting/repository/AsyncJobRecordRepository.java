package com.powergrid.forecasting.repository;

import com.powergrid.forecasting.entity.AsyncJobRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsyncJobRecordRepository extends JpaRepository<AsyncJobRecord, String> {

    List<AsyncJobRecord> findTop100ByOrderByCreatedAtDesc();

    List<AsyncJobRecord> findTop100ByCreatedByOrderByCreatedAtDesc(String createdBy);
}
