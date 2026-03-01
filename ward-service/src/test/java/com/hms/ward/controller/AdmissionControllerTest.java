package com.hms.ward.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.ward.dto.AdmissionDto;
import com.hms.ward.dto.DischargeRequest;
import com.hms.ward.exception.BusinessException;
import com.hms.ward.exception.ResourceNotFoundException;
import com.hms.ward.service.AdmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdmissionController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdmissionService admissionService;

    private AdmissionDto buildSampleAdmission() {
        return AdmissionDto.builder()
                .id(UUID.randomUUID())
                .patientId(UUID.randomUUID())
                .patientName("Andrea Lalema")
                .patientNumber("R00001")
                .bedId(UUID.randomUUID())
                .bedNumber("M-101")
                .wardId(UUID.randomUUID())
                .wardName("Male General Ward")
                .attendingDoctorId(UUID.randomUUID())
                .attendingDoctorName("Dr. Smith")
                .admissionReason("Typhoid fever")
                .status("admitted")
                .admittedAt(ZonedDateTime.now().minusDays(3))
                .services(List.of())
                .runningTotal(BigDecimal.ZERO)
                .daysAdmitted(3)
                .build();
    }

    // ─── POST /admissions ─────────────────────────────────────────────────────────

    @Test
    void admitPatient_withValidRequest_returns201() throws Exception {
        AdmissionDto created = buildSampleAdmission();
        when(admissionService.admitPatient(any())).thenReturn(created);

        String requestBody = """
                {
                  "patientId": "%s",
                  "patientName": "Andrea Lalema",
                  "bedId": "%s",
                  "attendingDoctorId": "%s",
                  "admissionReason": "Typhoid fever — IV treatment required"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("admitted"))
                .andExpect(jsonPath("$.data.patientName").value("Andrea Lalema"));
    }

    @Test
    void admitPatient_withMissingPatientId_returns400() throws Exception {
        String requestBody = """
                {
                  "bedId": "%s",
                  "attendingDoctorId": "%s",
                  "admissionReason": "Typhoid fever"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void admitPatient_whenBedOccupied_returns422() throws Exception {
        when(admissionService.admitPatient(any()))
                .thenThrow(new BusinessException("BED_NOT_AVAILABLE", "Bed M-101 is currently occupied", 422));

        String requestBody = """
                {
                  "patientId": "%s",
                  "bedId": "%s",
                  "attendingDoctorId": "%s",
                  "admissionReason": "Typhoid fever"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BED_NOT_AVAILABLE"));
    }

    @Test
    void admitPatient_whenAlreadyAdmitted_returns422() throws Exception {
        when(admissionService.admitPatient(any()))
                .thenThrow(new BusinessException("PATIENT_ALREADY_ADMITTED",
                        "This patient already has an active admission", 422));

        String requestBody = """
                {
                  "patientId": "%s",
                  "bedId": "%s",
                  "attendingDoctorId": "%s",
                  "admissionReason": "Typhoid fever"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PATIENT_ALREADY_ADMITTED"));
    }

    // ─── GET /admissions ──────────────────────────────────────────────────────────

    @Test
    void listAdmissions_returnsPagedResult() throws Exception {
        AdmissionDto admission = buildSampleAdmission();
        when(admissionService.listAdmissions(any(), any(), any(), any(), any(), eq(1), eq(20)))
                .thenReturn(new PageImpl<>(List.of(admission), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/admissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void listAdmissions_withStatusFilter_returns200() throws Exception {
        when(admissionService.listAdmissions(any(), eq("admitted"), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(buildSampleAdmission()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/admissions").param("status", "admitted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("admitted"));
    }

    // ─── GET /admissions/:id ──────────────────────────────────────────────────────

    @Test
    void getAdmission_whenFound_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        AdmissionDto admission = buildSampleAdmission();
        when(admissionService.getAdmissionById(id)).thenReturn(admission);

        mockMvc.perform(get("/admissions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.patientName").value("Andrea Lalema"))
                .andExpect(jsonPath("$.data.wardName").value("Male General Ward"));
    }

    @Test
    void getAdmission_whenNotFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(admissionService.getAdmissionById(id))
                .thenThrow(new ResourceNotFoundException("Admission not found: " + id));

        mockMvc.perform(get("/admissions/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // ─── PUT /admissions/:id/discharge ────────────────────────────────────────────

    @Test
    void dischargePatient_returns200WithFinalTotal() throws Exception {
        UUID id = UUID.randomUUID();
        AdmissionDto discharged = buildSampleAdmission();
        discharged.setStatus("discharged");
        discharged.setRunningTotal(new BigDecimal("12750.00"));
        when(admissionService.dischargePatient(eq(id), any())).thenReturn(discharged);

        String requestBody = """
                {
                  "dischargeNotes": "Patient recovered well.",
                  "dischargeDiagnosis": "Typhoid Fever resolved"
                }
                """;

        mockMvc.perform(put("/admissions/{id}/discharge", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("discharged"))
                .andExpect(jsonPath("$.message").value("Patient discharged. Ward invoice will be created automatically."))
                .andExpect(jsonPath("$.final_total").value(12750.00));
    }

    @Test
    void dischargePatient_whenAlreadyDischarged_returns422() throws Exception {
        UUID id = UUID.randomUUID();
        when(admissionService.dischargePatient(eq(id), any()))
                .thenThrow(new BusinessException("ALREADY_DISCHARGED", "Patient is already discharged", 422));

        mockMvc.perform(put("/admissions/{id}/discharge", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ALREADY_DISCHARGED"));
    }

    // ─── GET /patients/:patientId/admissions ──────────────────────────────────────

    @Test
    void getPatientAdmissionHistory_returns200() throws Exception {
        UUID patientId = UUID.randomUUID();
        when(admissionService.getPatientAdmissionHistory(eq(patientId), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(buildSampleAdmission()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/patients/{patientId}/admissions", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.total").value(1));
    }
}
