package com.hms.staff.service;

import com.hms.staff.dto.DepartmentCreateRequest;
import com.hms.staff.dto.DepartmentDto;
import com.hms.staff.dto.DepartmentUpdateRequest;
import com.hms.staff.entity.Department;
import com.hms.staff.entity.Doctor;
import com.hms.staff.exception.BusinessException;
import com.hms.staff.exception.ResourceNotFoundException;
import com.hms.staff.repository.DepartmentRepository;
import com.hms.staff.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final DoctorRepository doctorRepository;

    public List<DepartmentDto> listDepartments() {
        return departmentRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public DepartmentDto createDepartment(DepartmentCreateRequest request) {
        if (departmentRepository.existsByName(request.getName())) {
            throw new BusinessException("DEPARTMENT_NAME_EXISTS",
                    "Department with name '" + request.getName() + "' already exists", 409);
        }

        if (request.getHeadDoctorId() != null) {
            doctorRepository.findById(request.getHeadDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Head doctor not found: " + request.getHeadDoctorId()));
        }

        Department department = Department.builder()
                .name(request.getName())
                .description(request.getDescription())
                .headDoctorId(request.getHeadDoctorId())
                .isActive(true)
                .build();

        Department saved = departmentRepository.save(department);
        log.info("Created department: {} ({})", saved.getName(), saved.getId());
        return toDto(saved);
    }

    @Transactional
    public DepartmentDto updateDepartment(UUID id, DepartmentUpdateRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));

        if (request.getName() != null) {
            if (departmentRepository.existsByNameAndIdNot(request.getName(), id)) {
                throw new BusinessException("DEPARTMENT_NAME_EXISTS",
                        "Department with name '" + request.getName() + "' already exists", 409);
            }
            department.setName(request.getName());
        }
        if (request.getDescription() != null) department.setDescription(request.getDescription());
        if (request.getIsActive() != null) department.setIsActive(request.getIsActive());

        if (request.getHeadDoctorId() != null) {
            doctorRepository.findById(request.getHeadDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Head doctor not found: " + request.getHeadDoctorId()));
            department.setHeadDoctorId(request.getHeadDoctorId());
        }

        Department saved = departmentRepository.save(department);
        return toDto(saved);
    }

    @Transactional
    public void deleteDepartment(UUID id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));

        long activeDoctors = doctorRepository.countByDepartmentIdAndStatus(id, "active");
        if (activeDoctors > 0) {
            throw new BusinessException("DEPARTMENT_HAS_ACTIVE_DOCTORS",
                    "Cannot delete department with " + activeDoctors + " active doctor(s) assigned", 422);
        }

        departmentRepository.delete(department);
        log.info("Deleted department: {} ({})", department.getName(), id);
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private DepartmentDto toDto(Department dept) {
        long doctorCount = doctorRepository.countByDepartmentIdAndStatus(dept.getId(), "active");

        String headDoctorName = null;
        if (dept.getHeadDoctorId() != null) {
            Optional<Doctor> headDoctor = doctorRepository.findById(dept.getHeadDoctorId());
            headDoctorName = headDoctor.map(d -> "Dr. " + d.getFirstName() + " " + d.getLastName())
                    .orElse(null);
        }

        return DepartmentDto.builder()
                .id(dept.getId())
                .name(dept.getName())
                .description(dept.getDescription())
                .headDoctorId(dept.getHeadDoctorId())
                .headDoctorName(headDoctorName)
                .isActive(dept.getIsActive())
                .doctorCount(doctorCount)
                .build();
    }
}
