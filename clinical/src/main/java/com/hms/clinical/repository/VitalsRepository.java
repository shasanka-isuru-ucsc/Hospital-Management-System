package com.hms.clinical.repository;

import com.hms.clinical.entity.Vitals;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VitalsRepository extends JpaRepository<Vitals, UUID> {
    Optional<Vitals> findBySessionId(UUID sessionId);

    List<Vitals> findBySessionPatientIdAndRecordedAtBetweenOrderByRecordedAtDesc(UUID patientId,
            LocalDateTime startDate, LocalDateTime endDate);
}
