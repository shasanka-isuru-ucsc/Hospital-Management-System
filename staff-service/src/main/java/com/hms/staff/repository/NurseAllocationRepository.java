package com.hms.staff.repository;

import com.hms.staff.entity.NurseAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface NurseAllocationRepository extends JpaRepository<NurseAllocation, UUID>,
        JpaSpecificationExecutor<NurseAllocation> {
    boolean existsByNurseIdAndSessionDateAndStatus(UUID nurseId, LocalDate sessionDate, String status);
    List<NurseAllocation> findBySessionDateAndStatus(LocalDate sessionDate, String status);
    List<NurseAllocation> findByNurseIdAndSessionDateAndStatus(UUID nurseId, LocalDate sessionDate, String status);
}
