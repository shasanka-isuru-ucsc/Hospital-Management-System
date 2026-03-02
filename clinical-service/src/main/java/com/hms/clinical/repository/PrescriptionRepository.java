package com.hms.clinical.repository;

import com.hms.clinical.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {
    List<Prescription> findBySessionId(UUID sessionId);

    List<Prescription> findBySessionIdAndType(UUID sessionId, String type);

    @Query("SELECT p FROM Prescription p JOIN p.session s WHERE p.type = 'internal' AND p.status = :status AND s.status = 'completed' ORDER BY p.createdAt ASC")
    List<Prescription> findPharmacyQueue(String status);
}
