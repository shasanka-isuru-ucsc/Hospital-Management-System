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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock private DepartmentRepository departmentRepository;
    @Mock private DoctorRepository doctorRepository;

    @InjectMocks private DepartmentService departmentService;

    @Test
    void listDepartments_returnsMappedList() {
        UUID deptId = UUID.randomUUID();
        Department dept = Department.builder().id(deptId).name("Cardiology").isActive(true).build();
        when(departmentRepository.findAll()).thenReturn(List.of(dept));
        when(doctorRepository.countByDepartmentIdAndStatus(deptId, "active")).thenReturn(3L);

        List<DepartmentDto> result = departmentService.listDepartments();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Cardiology");
        assertThat(result.get(0).getDoctorCount()).isEqualTo(3L);
    }

    @Test
    void createDepartment_success() {
        DepartmentCreateRequest req = new DepartmentCreateRequest();
        req.setName("Neurology");

        when(departmentRepository.existsByName("Neurology")).thenReturn(false);
        Department saved = Department.builder().id(UUID.randomUUID()).name("Neurology").isActive(true).build();
        when(departmentRepository.save(any())).thenReturn(saved);
        when(doctorRepository.countByDepartmentIdAndStatus(any(), eq("active"))).thenReturn(0L);

        DepartmentDto result = departmentService.createDepartment(req);

        assertThat(result.getName()).isEqualTo("Neurology");
        verify(departmentRepository).save(any(Department.class));
    }

    @Test
    void createDepartment_duplicateName_throws409() {
        DepartmentCreateRequest req = new DepartmentCreateRequest();
        req.setName("Cardiology");
        when(departmentRepository.existsByName("Cardiology")).thenReturn(true);

        assertThatThrownBy(() -> departmentService.createDepartment(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists")
                .extracting("statusCode").isEqualTo(409);
    }

    @Test
    void deleteDepartment_withActiveDoctors_throws422() {
        UUID deptId = UUID.randomUUID();
        Department dept = Department.builder().id(deptId).name("Cardiology").isActive(true).build();
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        when(doctorRepository.countByDepartmentIdAndStatus(deptId, "active")).thenReturn(2L);

        assertThatThrownBy(() -> departmentService.deleteDepartment(deptId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("active doctor")
                .extracting("statusCode").isEqualTo(422);
    }

    @Test
    void deleteDepartment_noActiveDoctors_succeeds() {
        UUID deptId = UUID.randomUUID();
        Department dept = Department.builder().id(deptId).name("Radiology").isActive(true).build();
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        when(doctorRepository.countByDepartmentIdAndStatus(deptId, "active")).thenReturn(0L);

        departmentService.deleteDepartment(deptId);

        verify(departmentRepository).delete(dept);
    }

    @Test
    void updateDepartment_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(departmentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.updateDepartment(id, new DepartmentUpdateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateDepartment_withHeadDoctor_validatesDoctor() {
        UUID deptId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        Department dept = Department.builder().id(deptId).name("ENT").isActive(true).build();
        DepartmentUpdateRequest req = new DepartmentUpdateRequest();
        req.setHeadDoctorId(doctorId);

        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.updateDepartment(deptId, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Head doctor not found");
    }
}
