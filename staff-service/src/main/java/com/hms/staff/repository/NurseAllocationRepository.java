package com.hms.staff.repository;

import com.hms.staff.entity.NurseAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface NurseAllocationRepository extends JpaRepository<NurseAllocation, UUID> {

    @Query("SELECT n FROM NurseAllocation n WHERE " +
            "(:doctorId IS NULL OR n.doctor.id = :doctorId) AND " +
            "(:date IS NULL OR n.sessionDate = :date) AND " +
            "(:status IS NULL OR n.status = :status)")
    List<NurseAllocation> findAllWithFilters(
            @Param("doctorId") UUID doctorId,
            @Param("date") LocalDate date,
            @Param("status") String status);

    boolean existsByNurseIdAndSessionDateAndStatus(UUID nurseId, LocalDate sessionDate, String status);
}
