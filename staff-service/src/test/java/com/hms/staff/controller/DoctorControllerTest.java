package com.hms.staff.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.staff.dto.DoctorDto;
import com.hms.staff.dto.PaginationMeta;
import com.hms.staff.exception.BusinessException;
import com.hms.staff.exception.GlobalExceptionHandler;
import com.hms.staff.exception.ResourceNotFoundException;
import com.hms.staff.service.DoctorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DoctorController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DoctorControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private DoctorService doctorService;

    @Test
    void listDoctors_returns200WithPagination() throws Exception {
        DoctorDto doctor = buildDoctorDto();
        Page<DoctorDto> page = new PageImpl<>(List.of(doctor));
        when(doctorService.listDoctors(any(), any(), any(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].firstName").value("Jenny"))
                .andExpect(jsonPath("$.meta.page").value(1));
    }

    @Test
    void listDoctors_withFilters_returns200() throws Exception {
        when(doctorService.listDoctors(any(), eq("active"), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/doctors").param("status", "active").param("page", "1").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getDoctorById_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(doctorService.getDoctorById(id)).thenThrow(new ResourceNotFoundException("Doctor not found: " + id));

        mockMvc.perform(get("/doctors/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void getDoctorById_found_returns200() throws Exception {
        DoctorDto doctor = buildDoctorDto();
        when(doctorService.getDoctorById(doctor.getId())).thenReturn(doctor);

        mockMvc.perform(get("/doctors/{id}", doctor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("dr.jennysmith"));
    }

    @Test
    void deactivateDoctor_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Doctor not found: " + id))
                .when(doctorService).deactivateDoctor(id);

        mockMvc.perform(delete("/doctors/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void deactivateDoctor_success_returns200WithMessage() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/doctors/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Doctor deactivated. Keycloak account disabled."));
    }

    @Test
    void createDoctor_withMultipart_returns201() throws Exception {
        DoctorDto created = buildDoctorDto();
        when(doctorService.createDoctor(any(), any(), any())).thenReturn(created);

        UUID deptId = UUID.randomUUID();
        mockMvc.perform(multipart("/doctors")
                        .file(new MockMultipartFile("first_name", "Jenny".getBytes()))
                        .file(new MockMultipartFile("last_name", "Smith".getBytes()))
                        .file(new MockMultipartFile("username", "dr.jennysmith".getBytes()))
                        .file(new MockMultipartFile("email", "jenny@hospital.com".getBytes()))
                        .file(new MockMultipartFile("mobile", "+94771234567".getBytes()))
                        .file(new MockMultipartFile("password", "Password@123".getBytes()))
                        .file(new MockMultipartFile("date_of_birth", "1985-05-20".getBytes()))
                        .file(new MockMultipartFile("gender", "female".getBytes()))
                        .file(new MockMultipartFile("education", "MBBS".getBytes()))
                        .file(new MockMultipartFile("designation", "Senior Physician".getBytes()))
                        .file(new MockMultipartFile("department_id", deptId.toString().getBytes())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createDoctor_conflictUsername_returns409() throws Exception {
        when(doctorService.createDoctor(any(), any(), any()))
                .thenThrow(new BusinessException("USERNAME_TAKEN", "Username already taken", 409));

        UUID deptId = UUID.randomUUID();
        mockMvc.perform(multipart("/doctors")
                        .file(new MockMultipartFile("first_name", "Jenny".getBytes()))
                        .file(new MockMultipartFile("last_name", "Smith".getBytes()))
                        .file(new MockMultipartFile("username", "dr.jennysmith".getBytes()))
                        .file(new MockMultipartFile("email", "jenny@hospital.com".getBytes()))
                        .file(new MockMultipartFile("mobile", "+94771234567".getBytes()))
                        .file(new MockMultipartFile("password", "Password@123".getBytes()))
                        .file(new MockMultipartFile("date_of_birth", "1985-05-20".getBytes()))
                        .file(new MockMultipartFile("gender", "female".getBytes()))
                        .file(new MockMultipartFile("education", "MBBS".getBytes()))
                        .file(new MockMultipartFile("designation", "Senior Physician".getBytes()))
                        .file(new MockMultipartFile("department_id", deptId.toString().getBytes())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USERNAME_TAKEN"));
    }

    private DoctorDto buildDoctorDto() {
        return DoctorDto.builder()
                .id(UUID.randomUUID())
                .firstName("Jenny").lastName("Smith")
                .username("dr.jennysmith").email("jenny@hospital.com")
                .mobile("+94771234567").gender("female")
                .education("MBBS, MS").designation("Senior Physician")
                .status("active").joiningDate(LocalDate.now())
                .build();
    }
}
