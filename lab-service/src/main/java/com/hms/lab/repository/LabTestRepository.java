package com.hms.lab.repository;

import com.hms.lab.entity.LabTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface LabTestRepository extends JpaRepository<LabTest, UUID>, JpaSpecificationExecutor<LabTest> {

    Optional<LabTest> findByCode(String code);

    boolean existsByCode(String code);
}
