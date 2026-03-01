package com.hms.staff.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.staff.dto.DepartmentCreateRequest;
import com.hms.staff.dto.DepartmentDto;
import com.hms.staff.dto.DepartmentUpdateRequest;
import com.hms.staff.exception.BusinessException;
import com.hms.staff.exception.GlobalExceptionHandler;
import com.hms.staff.exception.ResourceNotFoundException;
import com.hms.staff.service.DepartmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DepartmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DepartmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private DepartmentService departmentService;

    @Test
    void listDepartments_returns200() throws Exception {
        DepartmentDto dept = buildDeptDto("Cardiology");
        when(departmentService.listDepartments()).thenReturn(List.of(dept));

        mockMvc.perform(get("/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Cardiology"));
    }

    @Test
    void createDepartment_returns201() throws Exception {
        DepartmentCreateRequest req = new DepartmentCreateRequest();
        req.setName("Cardiology");
        DepartmentDto dto = buildDeptDto("Cardiology");
        when(departmentService.createDepartment(any())).thenReturn(dto);

        mockMvc.perform(post("/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Cardiology"));
    }

    @Test
    void createDepartment_missingName_returns400() throws Exception {
        DepartmentCreateRequest req = new DepartmentCreateRequest(); // no name

        mockMvc.perform(post("/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createDepartment_duplicateName_returns409() throws Exception {
        DepartmentCreateRequest req = new DepartmentCreateRequest();
        req.setName("Cardiology");
        when(departmentService.createDepartment(any()))
                .thenThrow(new BusinessException("DEPARTMENT_NAME_EXISTS", "Already exists", 409));

        mockMvc.perform(post("/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEPARTMENT_NAME_EXISTS"));
    }

    @Test
    void updateDepartment_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        DepartmentUpdateRequest req = new DepartmentUpdateRequest();
        req.setName("Cardiology Updated");
        DepartmentDto dto = buildDeptDto("Cardiology Updated");
        when(departmentService.updateDepartment(eq(id), any())).thenReturn(dto);

        mockMvc.perform(put("/departments/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Cardiology Updated"));
    }

    @Test
    void deleteDepartment_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(departmentService).deleteDepartment(id);

        mockMvc.perform(delete("/departments/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteDepartment_withActiveDoctors_returns422() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new BusinessException("DEPARTMENT_HAS_ACTIVE_DOCTORS", "Cannot delete", 422))
                .when(departmentService).deleteDepartment(id);

        mockMvc.perform(delete("/departments/{id}", id))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("DEPARTMENT_HAS_ACTIVE_DOCTORS"));
    }

    @Test
    void deleteDepartment_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Not found")).when(departmentService).deleteDepartment(id);

        mockMvc.perform(delete("/departments/{id}", id))
                .andExpect(status().isNotFound());
    }

    private DepartmentDto buildDeptDto(String name) {
        return DepartmentDto.builder()
                .id(UUID.randomUUID()).name(name).isActive(true).doctorCount(0L).build();
    }
}
