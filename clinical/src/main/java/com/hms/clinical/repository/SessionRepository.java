package com.hms.clinical.repository;

import com.hms.clinical.entity.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID>, JpaSpecificationExecutor<Session> {

    Page<Session> findByPatientIdAndStatus(UUID patientId, String status, Pageable pageable);

    Page<Session> findByDoctorIdAndStatus(UUID doctorId, String status, Pageable pageable);

    Page<Session> findByPatientId(UUID patientId, Pageable pageable);
}
