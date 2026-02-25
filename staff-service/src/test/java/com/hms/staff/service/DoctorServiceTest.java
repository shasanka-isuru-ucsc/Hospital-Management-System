package com.hms.staff.service;

import com.hms.staff.dto.DoctorCreateRequest;
import com.hms.staff.dto.DoctorDto;
import com.hms.staff.entity.Department;
import com.hms.staff.entity.Doctor;
import com.hms.staff.repository.DepartmentRepository;
import com.hms.staff.repository.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private KeycloakService keycloakService;

    @Mock
    private MinioService minioService;

    @InjectMocks
    private DoctorService doctorService;

    private Department department;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        department = Department.builder()
                .id(UUID.randomUUID())
                .name("Cardiology")
                .build();

        doctor = Doctor.builder()
                .id(UUID.randomUUID())
                .firstName("Jenny")
                .lastName("Smith")
                .username("dr.jennysmith")
                .email("jenny@example.com")
                .department(department)
                .status("active")
                .build();
    }

    @Test
    void createDoctor_success() {
        DoctorCreateRequest request = DoctorCreateRequest.builder()
                .firstName("Jenny")
                .lastName("Smith")
                .username("dr.jennysmith")
                .email("jenny@example.com")
                .password("password")
                .departmentId(department.getId())
                .build();

        MultipartFile avatar = mock(MultipartFile.class);
        MultipartFile banner = mock(MultipartFile.class);

        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));
        when(keycloakService.createUser(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("kc-id");
        when(minioService.uploadFile(avatar, "avatar")).thenReturn("avatar-key");
        when(minioService.uploadFile(banner, "banner")).thenReturn("banner-key");
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doctor);

        DoctorDto result = doctorService.createDoctor(request, avatar, banner);

        assertNotNull(result);
        assertEquals("Jenny", result.getFirstName());
        verify(keycloakService).createUser(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(doctorRepository).save(any(Doctor.class));
    }

    @Test
    void getDoctorById_success() {
        UUID id = doctor.getId();
        when(doctorRepository.findById(id)).thenReturn(Optional.of(doctor));

        DoctorDto result = doctorService.getDoctorById(id);

        assertNotNull(result);
        assertEquals("Jenny", result.getFirstName());
    }

    @Test
    void deactivateDoctor_success() {
        UUID id = doctor.getId();
        doctor.setKeycloakUserId("kc-id");
        when(doctorRepository.findById(id)).thenReturn(Optional.of(doctor));

        doctorService.deactivateDoctor(id);

        assertEquals("inactive", doctor.getStatus());
        verify(keycloakService).deactivateUser("kc-id");
        verify(doctorRepository).save(doctor);
    }
}
