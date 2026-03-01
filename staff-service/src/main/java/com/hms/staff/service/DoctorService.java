package com.hms.staff.service;

import com.hms.staff.dto.DoctorCreateRequest;
import com.hms.staff.dto.DoctorDto;
import com.hms.staff.dto.DoctorUpdateRequest;
import com.hms.staff.dto.PaginationMeta;
import com.hms.staff.entity.Department;
import com.hms.staff.entity.Doctor;
import com.hms.staff.exception.BusinessException;
import com.hms.staff.exception.ResourceNotFoundException;
import com.hms.staff.repository.DepartmentRepository;
import com.hms.staff.repository.DoctorRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final StaffMinioService minioService;

    // ─── List ─────────────────────────────────────────────────────────────────

    public Page<DoctorDto> listDoctors(UUID departmentId, String status, String search, int page, int limit) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("createdAt").descending());

        Specification<Doctor> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (departmentId != null) predicates.add(cb.equal(root.get("departmentId"), departmentId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), pattern),
                        cb.like(cb.lower(root.get("lastName")), pattern),
                        cb.like(cb.lower(root.get("designation")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return doctorRepository.findAll(spec, pageable).map(this::toDto);
    }

    // ─── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public DoctorDto createDoctor(DoctorCreateRequest request, MultipartFile avatar, MultipartFile banner) {
        // Validate uniqueness
        if (doctorRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("USERNAME_TAKEN", "Username '" + request.getUsername() + "' is already taken", 409);
        }
        if (doctorRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("EMAIL_TAKEN", "Email '" + request.getEmail() + "' is already registered", 409);
        }

        // Validate department
        departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + request.getDepartmentId()));

        // Create Keycloak user
        String keycloakUserId = keycloakAdminService.createUser(
                request.getUsername(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                request.getPassword()
        );

        // Assign doctor role
        if (keycloakUserId != null) {
            keycloakAdminService.assignRealmRole(keycloakUserId, "doctor");
        }

        // Build doctor entity
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
                .departmentId(request.getDepartmentId())
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry())
                .stateProvince(request.getStateProvince())
                .postalCode(request.getPostalCode())
                .biography(request.getBiography())
                .status(request.getStatus() != null ? request.getStatus() : "active")
                .joiningDate(LocalDate.now())
                .build();

        Doctor saved = doctorRepository.save(doctor);

        // Upload images after save (we have the ID now)
        if (avatar != null && !avatar.isEmpty()) {
            String key = uploadAvatar(saved.getId(), avatar);
            saved.setAvatarKey(key);
        }
        if (banner != null && !banner.isEmpty()) {
            String key = uploadBanner(saved.getId(), banner);
            saved.setBannerKey(key);
        }

        if (doctor.getAvatarKey() != null || doctor.getBannerKey() != null) {
            saved = doctorRepository.save(saved);
        }

        log.info("Created doctor: {} {} ({})", saved.getFirstName(), saved.getLastName(), saved.getId());
        return toDto(saved);
    }

    // ─── Get by ID ────────────────────────────────────────────────────────────

    public DoctorDto getDoctorById(UUID id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + id));
        return toDto(doctor);
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    @Transactional
    public DoctorDto updateDoctor(UUID id, DoctorUpdateRequest request, MultipartFile avatar, MultipartFile banner) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + id));

        if (request.getEmail() != null && !request.getEmail().equals(doctor.getEmail())) {
            if (doctorRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
                throw new BusinessException("EMAIL_TAKEN", "Email '" + request.getEmail() + "' is already registered", 409);
            }
            doctor.setEmail(request.getEmail());
            if (doctor.getKeycloakUserId() != null) {
                keycloakAdminService.updateUserEmail(doctor.getKeycloakUserId(), request.getEmail());
            }
        }

        if (request.getFirstName() != null) doctor.setFirstName(request.getFirstName());
        if (request.getLastName() != null) doctor.setLastName(request.getLastName());
        if (request.getMobile() != null) doctor.setMobile(request.getMobile());
        if (request.getEducation() != null) doctor.setEducation(request.getEducation());
        if (request.getDesignation() != null) doctor.setDesignation(request.getDesignation());
        if (request.getAddress() != null) doctor.setAddress(request.getAddress());
        if (request.getCity() != null) doctor.setCity(request.getCity());
        if (request.getCountry() != null) doctor.setCountry(request.getCountry());
        if (request.getStateProvince() != null) doctor.setStateProvince(request.getStateProvince());
        if (request.getPostalCode() != null) doctor.setPostalCode(request.getPostalCode());
        if (request.getBiography() != null) doctor.setBiography(request.getBiography());
        if (request.getStatus() != null) doctor.setStatus(request.getStatus());

        if (request.getDepartmentId() != null) {
            departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + request.getDepartmentId()));
            doctor.setDepartmentId(request.getDepartmentId());
        }

        if (avatar != null && !avatar.isEmpty()) {
            String key = uploadAvatar(id, avatar);
            doctor.setAvatarKey(key);
        }
        if (banner != null && !banner.isEmpty()) {
            String key = uploadBanner(id, banner);
            doctor.setBannerKey(key);
        }

        Doctor saved = doctorRepository.save(doctor);
        return toDto(saved);
    }

    // ─── Deactivate ───────────────────────────────────────────────────────────

    @Transactional
    public void deactivateDoctor(UUID id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + id));

        doctor.setStatus("inactive");
        doctorRepository.save(doctor);

        if (doctor.getKeycloakUserId() != null) {
            keycloakAdminService.disableUser(doctor.getKeycloakUserId());
        }

        log.info("Deactivated doctor: {} ({})", doctor.getUsername(), id);
    }

    // ─── MinIO Helpers ────────────────────────────────────────────────────────

    private String uploadAvatar(UUID doctorId, MultipartFile file) {
        try {
            String key = "doctors/" + doctorId + "/avatar." + getExtension(file.getOriginalFilename());
            minioService.uploadAvatar(key, file.getInputStream(), file.getSize(),
                    file.getContentType());
            return key;
        } catch (Exception e) {
            log.error("Failed to upload avatar for doctor {}: {}", doctorId, e.getMessage());
            throw new BusinessException("UPLOAD_FAILED", "Failed to upload avatar image", 500);
        }
    }

    private String uploadBanner(UUID doctorId, MultipartFile file) {
        try {
            String key = "doctors/" + doctorId + "/banner." + getExtension(file.getOriginalFilename());
            minioService.uploadBanner(key, file.getInputStream(), file.getSize(),
                    file.getContentType());
            return key;
        } catch (Exception e) {
            log.error("Failed to upload banner for doctor {}: {}", doctorId, e.getMessage());
            throw new BusinessException("UPLOAD_FAILED", "Failed to upload banner image", 500);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    // ─── DTO Mapping ──────────────────────────────────────────────────────────

    public DoctorDto toDto(Doctor doctor) {
        String departmentName = null;
        if (doctor.getDepartmentId() != null) {
            departmentName = departmentRepository.findById(doctor.getDepartmentId())
                    .map(Department::getName).orElse(null);
        }

        String avatarUrl = null;
        if (doctor.getAvatarKey() != null) {
            avatarUrl = minioService.generateAvatarPresignedUrl(doctor.getAvatarKey(), 60);
        }

        String bannerUrl = null;
        if (doctor.getBannerKey() != null) {
            bannerUrl = minioService.generateBannerPresignedUrl(doctor.getBannerKey(), 60);
        }

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
                .departmentId(doctor.getDepartmentId())
                .departmentName(departmentName)
                .address(doctor.getAddress())
                .city(doctor.getCity())
                .country(doctor.getCountry())
                .stateProvince(doctor.getStateProvince())
                .postalCode(doctor.getPostalCode())
                .biography(doctor.getBiography())
                .avatarUrl(avatarUrl)
                .bannerUrl(bannerUrl)
                .status(doctor.getStatus())
                .joiningDate(doctor.getJoiningDate())
                .createdAt(doctor.getCreatedAt())
                .build();
    }
}
