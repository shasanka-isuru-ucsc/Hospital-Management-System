package com.hms.ward.repository;

import com.hms.ward.entity.Admission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdmissionRepository extends JpaRepository<Admission, UUID>, JpaSpecificationExecutor<Admission> {
    Optional<Admission> findByPatientIdAndStatus(UUID patientId, String status);
    List<Admission> findByPatientIdOrderByAdmittedAtDesc(UUID patientId);
}
