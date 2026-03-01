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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NurseAllocationServiceTest {

    @Mock private NurseAllocationRepository nurseAllocationRepository;
    @Mock private StaffMemberRepository staffMemberRepository;
    @Mock private DoctorRepository doctorRepository;

    @InjectMocks private NurseAllocationService nurseAllocationService;

    @Test
    void allocateNurse_nurseNotFound_throws404() {
        NurseAllocationCreateRequest req = buildRequest();
        when(staffMemberRepository.findById(req.getNurseId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nurseAllocationService.allocateNurse(req, "admin"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Nurse not found");
    }

    @Test
    void allocateNurse_notANurse_throws422() {
        NurseAllocationCreateRequest req = buildRequest();
        StaffMember receptionist = buildStaffMember(req.getNurseId(), "receptionist");
        when(staffMemberRepository.findById(req.getNurseId())).thenReturn(Optional.of(receptionist));

        assertThatThrownBy(() -> nurseAllocationService.allocateNurse(req, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not a nurse")
                .extracting("statusCode").isEqualTo(422);
    }

    @Test
    void allocateNurse_doctorNotFound_throws404() {
        NurseAllocationCreateRequest req = buildRequest();
        StaffMember nurse = buildStaffMember(req.getNurseId(), "nurse");
        when(staffMemberRepository.findById(req.getNurseId())).thenReturn(Optional.of(nurse));
        when(doctorRepository.findById(req.getDoctorId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nurseAllocationService.allocateNurse(req, "admin"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Doctor not found");
    }

    @Test
    void allocateNurse_alreadyAllocated_throws422() {
        NurseAllocationCreateRequest req = buildRequest();
        StaffMember nurse = buildStaffMember(req.getNurseId(), "nurse");
        Doctor doctor = buildDoctor(req.getDoctorId());

        when(staffMemberRepository.findById(req.getNurseId())).thenReturn(Optional.of(nurse));
        when(doctorRepository.findById(req.getDoctorId())).thenReturn(Optional.of(doctor));
        when(nurseAllocationRepository.existsByNurseIdAndSessionDateAndStatus(
                req.getNurseId(), req.getSessionDate(), "active")).thenReturn(true);

        assertThatThrownBy(() -> nurseAllocationService.allocateNurse(req, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already allocated")
                .extracting("statusCode").isEqualTo(422);
    }

    @Test
    void allocateNurse_success() {
        NurseAllocationCreateRequest req = buildRequest();
        StaffMember nurse = buildStaffMember(req.getNurseId(), "nurse");
        Doctor doctor = buildDoctor(req.getDoctorId());

        when(staffMemberRepository.findById(req.getNurseId())).thenReturn(Optional.of(nurse));
        when(doctorRepository.findById(req.getDoctorId())).thenReturn(Optional.of(doctor));
        when(nurseAllocationRepository.existsByNurseIdAndSessionDateAndStatus(any(), any(), eq("active")))
                .thenReturn(false);

        NurseAllocation saved = NurseAllocation.builder()
                .id(UUID.randomUUID())
                .nurseId(req.getNurseId()).nurseName("Sarah Williams")
                .doctorId(req.getDoctorId()).doctorName("Dr. John Smith")
                .sessionDate(req.getSessionDate()).status("active")
                .allocatedAt(ZonedDateTime.now()).allocatedBy("admin")
                .build();
        when(nurseAllocationRepository.save(any())).thenReturn(saved);

        NurseAllocationDto result = nurseAllocationService.allocateNurse(req, "admin");

        assertThat(result.getNurseName()).isEqualTo("Sarah Williams");
        assertThat(result.getStatus()).isEqualTo("active");
    }

    @Test
    void completeAllocation_notActive_throws422() {
        UUID id = UUID.randomUUID();
        NurseAllocation allocation = buildAllocation(id, "completed");
        when(nurseAllocationRepository.findById(id)).thenReturn(Optional.of(allocation));

        assertThatThrownBy(() -> nurseAllocationService.completeAllocation(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already completed")
                .extracting("statusCode").isEqualTo(422);
    }

    @Test
    void completeAllocation_success() {
        UUID id = UUID.randomUUID();
        NurseAllocation allocation = buildAllocation(id, "active");
        when(nurseAllocationRepository.findById(id)).thenReturn(Optional.of(allocation));
        when(nurseAllocationRepository.save(any())).thenReturn(allocation);

        NurseAllocationDto result = nurseAllocationService.completeAllocation(id);

        assertThat(result.getStatus()).isEqualTo("completed");
    }

    @Test
    void cancelAllocation_alreadyCompleted_throws422() {
        UUID id = UUID.randomUUID();
        NurseAllocation allocation = buildAllocation(id, "completed");
        when(nurseAllocationRepository.findById(id)).thenReturn(Optional.of(allocation));

        assertThatThrownBy(() -> nurseAllocationService.cancelAllocation(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot cancel a completed")
                .extracting("statusCode").isEqualTo(422);
    }

    @Test
    void cancelAllocation_success() {
        UUID id = UUID.randomUUID();
        NurseAllocation allocation = buildAllocation(id, "active");
        when(nurseAllocationRepository.findById(id)).thenReturn(Optional.of(allocation));
        when(nurseAllocationRepository.save(any())).thenReturn(allocation);

        NurseAllocationDto result = nurseAllocationService.cancelAllocation(id);

        assertThat(result.getStatus()).isEqualTo("cancelled");
    }

    @Test
    void listAllocations_returnsFilteredList() {
        UUID allocationId = UUID.randomUUID();
        NurseAllocation allocation = buildAllocation(allocationId, "active");
        when(nurseAllocationRepository.findAll(any(Specification.class))).thenReturn(List.of(allocation));

        List<NurseAllocationDto> result = nurseAllocationService.listAllocations(null, null, "active");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("active");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private NurseAllocationCreateRequest buildRequest() {
        NurseAllocationCreateRequest req = new NurseAllocationCreateRequest();
        req.setNurseId(UUID.randomUUID());
        req.setDoctorId(UUID.randomUUID());
        req.setSessionDate(LocalDate.of(2026, 3, 10));
        return req;
    }

    private StaffMember buildStaffMember(UUID id, String role) {
        return StaffMember.builder()
                .id(id).fullName("Sarah Williams").role(role).status("active").build();
    }

    private Doctor buildDoctor(UUID id) {
        return Doctor.builder()
                .id(id).firstName("John").lastName("Smith")
                .username("dr.john").status("active").build();
    }

    private NurseAllocation buildAllocation(UUID id, String status) {
        return NurseAllocation.builder()
                .id(id).nurseId(UUID.randomUUID()).nurseName("Sarah Williams")
                .doctorId(UUID.randomUUID()).doctorName("Dr. John Smith")
                .sessionDate(LocalDate.of(2026, 3, 10)).status(status)
                .allocatedAt(ZonedDateTime.now()).allocatedBy("admin")
                .build();
    }
}
