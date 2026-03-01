package com.hms.staff.service;

import com.hms.staff.dto.StaffMemberCreateRequest;
import com.hms.staff.dto.StaffMemberDto;
import com.hms.staff.entity.NurseAllocation;
import com.hms.staff.entity.StaffMember;
import com.hms.staff.exception.ResourceNotFoundException;
import com.hms.staff.repository.NurseAllocationRepository;
import com.hms.staff.repository.StaffMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffMemberService {

    private final StaffMemberRepository staffMemberRepository;
    private final NurseAllocationRepository nurseAllocationRepository;

    // ─── Create Staff Member (admin) ──────────────────────────────────────────

    @Transactional
    public StaffMemberDto createStaffMember(StaffMemberCreateRequest request) {
        StaffMember member = StaffMember.builder()
                .fullName(request.getFullName())
                .role(request.getRole().toLowerCase())
                .email(request.getEmail())
                .mobile(request.getMobile())
                .status(request.getStatus() != null ? request.getStatus() : "active")
                .keycloakUserId(request.getKeycloakUserId())
                .build();

        StaffMember saved = staffMemberRepository.save(member);
        log.info("Created staff member: {} ({})", saved.getFullName(), saved.getId());
        return toDto(saved);
    }

    // ─── Get Available Staff ──────────────────────────────────────────────────

    public List<StaffMemberDto> getAvailableStaff(LocalDate date, String role) {
        String roleFilter = (role != null && !role.isBlank()) ? role.toLowerCase() : "nurse";

        List<StaffMember> allActiveStaff = staffMemberRepository.findByRoleAndStatus(roleFilter, "active");

        // Get nurse IDs already allocated (active) on this date
        List<NurseAllocation> activeAllocations = nurseAllocationRepository
                .findBySessionDateAndStatus(date, "active");

        Set<UUID> allocatedNurseIds = activeAllocations.stream()
                .map(NurseAllocation::getNurseId)
                .collect(Collectors.toSet());

        return allActiveStaff.stream()
                .filter(s -> !allocatedNurseIds.contains(s.getId()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ─── Get all staff members ────────────────────────────────────────────────

    public List<StaffMemberDto> listAllStaff() {
        return staffMemberRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public StaffMemberDto getById(UUID id) {
        StaffMember member = staffMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found: " + id));
        return toDto(member);
    }

    // ─── DTO ──────────────────────────────────────────────────────────────────

    private StaffMemberDto toDto(StaffMember s) {
        return StaffMemberDto.builder()
                .id(s.getId())
                .fullName(s.getFullName())
                .role(s.getRole())
                .email(s.getEmail())
                .mobile(s.getMobile())
                .status(s.getStatus())
                .build();
    }
}
