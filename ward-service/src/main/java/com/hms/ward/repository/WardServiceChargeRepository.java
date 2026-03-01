package com.hms.ward.repository;

import com.hms.ward.entity.WardServiceCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WardServiceChargeRepository extends JpaRepository<WardServiceCharge, UUID> {
    List<WardServiceCharge> findByAdmissionIdOrderByProvidedAtAsc(UUID admissionId);
    List<WardServiceCharge> findByAdmissionIdAndServiceTypeOrderByProvidedAtAsc(UUID admissionId, String serviceType);
    Optional<WardServiceCharge> findByIdAndAdmissionId(UUID id, UUID admissionId);
}
