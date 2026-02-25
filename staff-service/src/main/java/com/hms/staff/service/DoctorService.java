package com.hms.staff.service;

import com.hms.staff.dto.DoctorCreateRequest;
import com.hms.staff.dto.DoctorDto;
import com.hms.staff.dto.PaginationMeta;
import com.hms.staff.entity.Department;
import com.hms.staff.entity.Doctor;
import com.hms.staff.exception.ResourceNotFoundException;
import com.hms.staff.repository.DepartmentRepository;
import com.hms.staff.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final KeycloakService keycloakService;
    private final MinioService minioService;

    public Page<DoctorDto> getDoctors(UUID departmentId, String status, String search, int page, int limit) {
        PageRequest pageRequest = PageRequest.of(page - 1, limit);
        Page<Doctor> doctors = doctorRepository.findAllWithFilters(departmentId, status, search, pageRequest);
        return doctors.map(this::convertToDto);
    }

    @Transactional
    public DoctorDto createDoctor(DoctorCreateRequest request, MultipartFile avatar, MultipartFile banner) {
        log.info("Creating doctor profile and Keycloak account for user: {}", request.getUsername());

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department not found with id: " + request.getDepartmentId()));

        // 1. Create Keycloak User
        String keycloakUserId = keycloakService.createUser(
                request.getUsername(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                request.getPassword());

        // 2. Upload images to MinIO
        String avatarKey = minioService.uploadFile(avatar, "avatar");
        String bannerKey = minioService.uploadFile(banner, "banner");

        // 3. Save to Database
        Doctor doctor = Doctor.builder()
                .keycloakUserId(keycloakUserId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .username(request.getUsername())
                .email(request.getEmail())
                .mobile(request.getMobile())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .education(request.getEducation())
                .designation(request.getDesignation())
                .department(department)
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry())
                .stateProvince(request.getStateProvince())
                .postalCode(request.getPostalCode())
                .biography(request.getBiography())
                .avatarUrl(avatarKey)
                .bannerUrl(bannerKey)
                .status(request.getStatus() != null ? request.getStatus() : "active")
                .joiningDate(LocalDate.now())
                .build();

        return convertToDto(doctorRepository.save(doctor));
    }

    public DoctorDto getDoctorById(UUID id) {
        return doctorRepository.findById(id)
                .map(this::convertToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id: " + id));
    }

    @Transactional
    public DoctorDto updateDoctor(UUID id, DoctorCreateRequest request, MultipartFile avatar, MultipartFile banner) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id: " + id));

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Department not found with id: " + request.getDepartmentId()));
            doctor.setDepartment(department);
        }

        if (request.getFirstName() != null)
            doctor.setFirstName(request.getFirstName());
        if (request.getLastName() != null)
            doctor.setLastName(request.getLastName());
        if (request.getEmail() != null)
            doctor.setEmail(request.getEmail());
        if (request.getMobile() != null)
            doctor.setMobile(request.getMobile());
        if (request.getEducation() != null)
            doctor.setEducation(request.getEducation());
        if (request.getDesignation() != null)
            doctor.setDesignation(request.getDesignation());
        if (request.getAddress() != null)
            doctor.setAddress(request.getAddress());
        if (request.getCity() != null)
            doctor.setCity(request.getCity());
        if (request.getCountry() != null)
            doctor.setCountry(request.getCountry());
        if (request.getBiography() != null)
            doctor.setBiography(request.getBiography());
        if (request.getStatus() != null)
            doctor.setStatus(request.getStatus());

        if (avatar != null && !avatar.isEmpty()) {
            doctor.setAvatarUrl(minioService.uploadFile(avatar, "avatar"));
        }
        if (banner != null && !banner.isEmpty()) {
            doctor.setBannerUrl(minioService.uploadFile(banner, "banner"));
        }

        return convertToDto(doctorRepository.save(doctor));
    }

    @Transactional
    public void deactivateDoctor(UUID id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id: " + id));

        doctor.setStatus("inactive");
        doctorRepository.save(doctor);

        keycloakService.deactivateUser(doctor.getKeycloakUserId());
    }

    private DoctorDto convertToDto(Doctor doctor) {
        return DoctorDto.builder()
                .id(doctor.getId())
                .keycloakUserId(doctor.getKeycloakUserId())
                .firstName(doctor.getFirstName())
                .lastName(doctor.getLastName())
                .username(doctor.getUsername())
                .email(doctor.getEmail())
                .mobile(doctor.getMobile())
                .dateOfBirth(doctor.getDateOfBirth())
                .gender(doctor.getGender())
                .education(doctor.getEducation())
                .designation(doctor.getDesignation())
                .departmentId(doctor.getDepartment().getId())
                .departmentName(doctor.getDepartment().getName())
                .address(doctor.getAddress())
                .city(doctor.getCity())
                .country(doctor.getCountry())
                .stateProvince(doctor.getStateProvince())
                .postalCode(doctor.getPostalCode())
                .biography(doctor.getBiography())
                .avatarUrl(minioService.getPresignedUrl(doctor.getAvatarUrl(), "avatar"))
                .bannerUrl(minioService.getPresignedUrl(doctor.getBannerUrl(), "banner"))
                .status(doctor.getStatus())
                .joiningDate(doctor.getJoiningDate())
                .createdAt(doctor.getCreatedAt())
                .build();
    }
}
