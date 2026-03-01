package com.hms.lab.repository;

import com.hms.lab.entity.LabOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface LabOrderRepository extends JpaRepository<LabOrder, UUID>, JpaSpecificationExecutor<LabOrder> {

    Optional<LabOrder> findBySessionId(UUID sessionId);
}
