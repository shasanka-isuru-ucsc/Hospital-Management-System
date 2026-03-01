package com.hms.staff.service;

import com.hms.staff.dto.DoctorCreateRequest;
import com.hms.staff.dto.DoctorDto;
import com.hms.staff.dto.DoctorUpdateRequest;
import com.hms.staff.entity.Department;
import com.hms.staff.entity.Doctor;
import com.hms.staff.exception.BusinessException;
import com.hms.staff.exception.ResourceNotFoundException;
import com.hms.staff.repository.DepartmentRepository;
import com.hms.staff.repository.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock private DoctorRepository doctorRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private StaffMinioService minioService;

    @InjectMocks private DoctorService doctorService;

    private UUID deptId;
    private Department testDept;

    @BeforeEach
    void setup() {
        deptId = UUID.randomUUID();
        testDept = Department.builder().id(deptId).name("Cardiology").isActive(true).build();
    }

    @Test
    void listDoctors_returnsPagedResults() {
        Doctor doctor = buildDoctor(deptId);
        Page<Doctor> page = new PageImpl<>(List.of(doctor));
        when(doctorRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(testDept));

        Page<DoctorDto> result = doctorService.listDoctors(null, "active", null, 1, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getFirstName()).isEqualTo("Jenny");
    }

    @Test
    void createDoctor_success() {
        DoctorCreateRequest req = buildCreateRequest();
        when(doctorRepository.existsByUsername(req.getUsername())).thenReturn(false);
        when(doctorRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(testDept));
        when(keycloakAdminService.createUser(any(), any(), any(), any(), any())).thenReturn("kc-id-123");

        Doctor savedDoctor = buildDoctor(deptId);
        savedDoctor.setKeycloakUserId("kc-id-123");
        when(doctorRepository.save(any())).thenReturn(savedDoctor);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(testDept));

        DoctorDto result = doctorService.createDoctor(req, null, null);

        assertThat(result.getFirstName()).isEqualTo("Jenny");
        verify(keycloakAdminService).createUser(any(), any(), any(), any(), any());
        verify(keycloakAdminService).assignRealmRole("kc-id-123", "doctor");
    }

    @Test
    void createDoctor_duplicateUsername_throws409() {
        DoctorCreateRequest req = buildCreateRequest();
        when(doctorRepository.existsByUsername(req.getUsername())).thenReturn(true);

        assertThatThrownBy(() -> doctorService.createDoctor(req, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("statusCode").isEqualTo(409);
    }

    @Test
    void createDoctor_duplicateEmail_throws409() {
        DoctorCreateRequest req = buildCreateRequest();
        when(doctorRepository.existsByUsername(req.getUsername())).thenReturn(false);
        when(doctorRepository.existsByEmail(req.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> doctorService.createDoctor(req, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("statusCode").isEqualTo(409);
    }

    @Test
    void createDoctor_invalidDepartment_throws404() {
        DoctorCreateRequest req = buildCreateRequest();
        when(doctorRepository.existsByUsername(req.getUsername())).thenReturn(false);
        when(doctorRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> doctorService.createDoctor(req, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getDoctorById_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(doctorRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> doctorService.getDoctorById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getDoctorById_found_returnsDto() {
        Doctor doctor = buildDoctor(deptId);
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(testDept));

        DoctorDto result = doctorService.getDoctorById(doctor.getId());

        assertThat(result.getFirstName()).isEqualTo("Jenny");
        assertThat(result.getDepartmentName()).isEqualTo("Cardiology");
    }

    @Test
    void deactivateDoctor_setsInactiveAndDisablesKeycloak() {
        Doctor doctor = buildDoctor(deptId);
        doctor.setKeycloakUserId("kc-id-xyz");
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(doctorRepository.save(any())).thenReturn(doctor);

        doctorService.deactivateDoctor(doctor.getId());

        assertThat(doctor.getStatus()).isEqualTo("inactive");
        verify(keycloakAdminService).disableUser("kc-id-xyz");
    }

    @Test
    void updateDoctor_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(doctorRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> doctorService.updateDoctor(id, new DoctorUpdateRequest(), null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Doctor buildDoctor(UUID departmentId) {
        return Doctor.builder()
                .id(UUID.randomUUID())
                .firstName("Jenny")
                .lastName("Smith")
                .username("dr.jennysmith")
                .email("jenny@hospital.com")
                .mobile("+94771234567")
                .dateOfBirth(LocalDate.of(1985, 5, 20))
                .gender("female")
                .education("MBBS, MS")
                .designation("Senior Physician")
                .departmentId(departmentId)
                .status("active")
                .joiningDate(LocalDate.now())
                .createdAt(ZonedDateTime.now())
                .build();
    }

    private DoctorCreateRequest buildCreateRequest() {
        DoctorCreateRequest req = new DoctorCreateRequest();
        req.setFirstName("Jenny");
        req.setLastName("Smith");
        req.setUsername("dr.jennysmith");
        req.setEmail("jenny@hospital.com");
        req.setMobile("+94771234567");
        req.setPassword("Password@123");
        req.setDateOfBirth(LocalDate.of(1985, 5, 20));
        req.setGender("female");
        req.setEducation("MBBS, MS");
        req.setDesignation("Senior Physician");
        req.setDepartmentId(deptId);
        return req;
    }
}
