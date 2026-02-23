package com.hms.clinical.repository;

import com.hms.clinical.entity.LabRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LabRequestRepository extends JpaRepository<LabRequest, UUID> {
    List<LabRequest> findBySessionId(UUID sessionId);
}
