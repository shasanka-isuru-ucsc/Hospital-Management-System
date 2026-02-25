package com.hms.staff.service;

import com.hms.staff.dto.NurseAllocationDto;
import com.hms.staff.dto.StaffAvailableDto;
import com.hms.staff.entity.Doctor;
import com.hms.staff.entity.NurseAllocation;
import com.hms.staff.exception.BusinessException;
import com.hms.staff.exception.ResourceNotFoundException;
import com.hms.staff.repository.DoctorRepository;
import com.hms.staff.repository.NurseAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NurseAllocationService {

    private final NurseAllocationRepository nurseAllocationRepository;
    private final DoctorRepository doctorRepository;

    public List<NurseAllocationDto> getAllocations(UUID doctorId, LocalDate date, String status) {
        return nurseAllocationRepository.findAllWithFilters(doctorId, date, status).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public NurseAllocationDto allocateNurse(NurseAllocationDto dto) {
        Doctor doctor = doctorRepository.findById(dto.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id: " + dto.getDoctorId()));

        // Validation: Nurse cannot have multiple active allocations on the same day
        if (nurseAllocationRepository.existsByNurseIdAndSessionDateAndStatus(dto.getNurseId(), dto.getSessionDate(),
                "active")) {
            throw new BusinessException("NURSE_ALREADY_ALLOCATED",
                    "Nurse is already allocated to another doctor on " + dto.getSessionDate(), 422);
        }

        NurseAllocation allocation = NurseAllocation.builder()
                .nurseId(dto.getNurseId())
                .nurseName(dto.getNurseName())
                .doctor(doctor)
                .sessionDate(dto.getSessionDate())
                .status("active")
                .allocatedBy(dto.getAllocatedBy())
                .build();

        return convertToDto(nurseAllocationRepository.save(allocation));
    }

    @Transactional
    public NurseAllocationDto completeAllocation(UUID id) {
        NurseAllocation allocation = nurseAllocationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Allocation not found with id: " + id));
        allocation.setStatus("completed");
        return convertToDto(nurseAllocationRepository.save(allocation));
    }

    @Transactional
    public void cancelAllocation(UUID id) {
        NurseAllocation allocation = nurseAllocationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Allocation not found with id: " + id));
        allocation.setStatus("cancelled");
        nurseAllocationRepository.save(allocation);
    }

    public List<StaffAvailableDto> getAvailableStaff(LocalDate date, String role) {
        // In a real system, this would query a central Staff/Employee service.
        // For now, we return a mock list and filter out those already allocated for the
        // date.
        List<StaffAvailableDto> allNurses = new ArrayList<>();
        allNurses.add(StaffAvailableDto.builder().id(UUID.fromString("617495f3-529a-4712-9c76-2e860953c89f"))
                .fullName("Sarah Williams").role("nurse").mobile("+94771112233").build());
        allNurses.add(StaffAvailableDto.builder().id(UUID.fromString("b9c8d7a6-e5f4-3210-fedc-ba9876543210"))
                .fullName("John Smith").role("nurse").mobile("+94774445566").build());

        return allNurses.stream()
                .filter(n -> !nurseAllocationRepository.existsByNurseIdAndSessionDateAndStatus(n.getId(), date,
                        "active"))
                .collect(Collectors.toList());
    }

    private NurseAllocationDto convertToDto(NurseAllocation allocation) {
        return NurseAllocationDto.builder()
                .id(allocation.getId())
                .nurseId(allocation.getNurseId())
                .nurseName(allocation.getNurseName())
                .doctorId(allocation.getDoctor().getId())
                .doctorName(allocation.getDoctor().getFirstName() + " " + allocation.getDoctor().getLastName())
                .sessionDate(allocation.getSessionDate())
                .status(allocation.getStatus())
                .allocatedAt(allocation.getAllocatedAt())
                .allocatedBy(allocation.getAllocatedBy())
                .build();
    }
}
