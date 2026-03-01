package com.hms.staff.service;

import com.hms.staff.dto.NurseAllocationCreateRequest;
import com.hms.staff.dto.NurseAllocationDto;
import com.hms.staff.entity.Doctor;
import com.hms.staff.entity.NurseAllocation;
import com.hms.staff.entity.StaffMember;
import com.hms.staff.exception.BusinessException;
import com.hms.staff.exception.ResourceNotFoundException;
import com.hms.staff.repository.DoctorRepository;
import com.hms.staff.repository.NurseAllocationRepository;
import com.hms.staff.repository.StaffMemberRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseAllocationService {

    private final NurseAllocationRepository nurseAllocationRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final DoctorRepository doctorRepository;

    // ─── List Allocations ─────────────────────────────────────────────────────

    public List<NurseAllocationDto> listAllocations(UUID doctorId, LocalDate date, String status) {
        var spec = (org.springframework.data.jpa.domain.Specification<NurseAllocation>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (doctorId != null) predicates.add(cb.equal(root.get("doctorId"), doctorId));
            if (date != null) predicates.add(cb.equal(root.get("sessionDate"), date));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return nurseAllocationRepository.findAll(spec).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ─── Allocate Nurse ───────────────────────────────────────────────────────

    @Transactional
    public NurseAllocationDto allocateNurse(NurseAllocationCreateRequest request, String allocatedBy) {
        // Validate nurse exists
        StaffMember nurse = staffMemberRepository.findById(request.getNurseId())
                .orElseThrow(() -> new ResourceNotFoundException("Nurse not found: " + request.getNurseId()));

        if (!"nurse".equalsIgnoreCase(nurse.getRole())) {
            throw new BusinessException("NOT_A_NURSE",
                    "Staff member '" + nurse.getFullName() + "' is not a nurse", 422);
        }

        // Validate doctor exists
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + request.getDoctorId()));

        // Check nurse not already allocated (active) on this date
        boolean alreadyAllocated = nurseAllocationRepository.existsByNurseIdAndSessionDateAndStatus(
                request.getNurseId(), request.getSessionDate(), "active");

        if (alreadyAllocated) {
            throw new BusinessException("NURSE_ALREADY_ALLOCATED",
                    nurse.getFullName() + " is already allocated to another doctor on "
                            + request.getSessionDate(), 422);
        }

        NurseAllocation allocation = NurseAllocation.builder()
                .nurseId(request.getNurseId())
                .nurseName(nurse.getFullName())
                .doctorId(request.getDoctorId())
                .doctorName("Dr. " + doctor.getFirstName() + " " + doctor.getLastName())
                .sessionDate(request.getSessionDate())
                .status("active")
                .allocatedBy(allocatedBy)
                .build();

        NurseAllocation saved = nurseAllocationRepository.save(allocation);
        log.info("Allocated nurse {} to doctor {} on {}", nurse.getFullName(),
                doctor.getLastName(), request.getSessionDate());
        return toDto(saved);
    }

    // ─── Complete Allocation ──────────────────────────────────────────────────

    @Transactional
    public NurseAllocationDto completeAllocation(UUID id) {
        NurseAllocation allocation = nurseAllocationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Allocation not found: " + id));

        if (!"active".equals(allocation.getStatus())) {
            throw new BusinessException("ALLOCATION_NOT_ACTIVE",
                    "Allocation is already " + allocation.getStatus(), 422);
        }

        allocation.setStatus("completed");
        NurseAllocation saved = nurseAllocationRepository.save(allocation);
        log.info("Completed nurse allocation: {}", id);
        return toDto(saved);
    }

    // ─── Cancel Allocation ────────────────────────────────────────────────────

    @Transactional
    public NurseAllocationDto cancelAllocation(UUID id) {
        NurseAllocation allocation = nurseAllocationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Allocation not found: " + id));

        if ("completed".equals(allocation.getStatus())) {
            throw new BusinessException("ALLOCATION_ALREADY_COMPLETED",
                    "Cannot cancel a completed allocation", 422);
        }

        allocation.setStatus("cancelled");
        NurseAllocation saved = nurseAllocationRepository.save(allocation);
        log.info("Cancelled nurse allocation: {}", id);
        return toDto(saved);
    }

    // ─── DTO ──────────────────────────────────────────────────────────────────

    private NurseAllocationDto toDto(NurseAllocation a) {
        return NurseAllocationDto.builder()
                .id(a.getId())
                .nurseId(a.getNurseId())
                .nurseName(a.getNurseName())
                .doctorId(a.getDoctorId())
                .doctorName(a.getDoctorName())
                .sessionDate(a.getSessionDate())
                .status(a.getStatus())
                .allocatedAt(a.getAllocatedAt())
                .allocatedBy(a.getAllocatedBy())
                .build();
    }
}
