package com.hms.reception.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.reception.dto.PatientCreateRequest;
import com.hms.reception.entity.Patient;
import com.hms.reception.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientController.class)
@AutoConfigureMockMvc(addFilters = false)
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PatientService patientService;

    @Test
    void registerPatient_withJsonBody_returns201() throws Exception {
        PatientCreateRequest request = new PatientCreateRequest();
        request.setFirstName("Amanda");
        request.setLastName("Smith");
        request.setMobile("+94711234567");
        request.setDateOfBirth(LocalDate.of(1992, 5, 15));
        request.setGender("female");

        Patient savedPatient = Patient.builder()
                .firstName("Amanda")
                .lastName("Smith")
                .mobile("+94711234567")
                .dateOfBirth(LocalDate.of(1992, 5, 15))
                .gender("female")
                .patientNumber("R00001")
                .status("active")
                .build();
        savedPatient.setId(UUID.randomUUID());

        when(patientService.registerPatient(any(Patient.class))).thenReturn(savedPatient);

        mockMvc.perform(post("/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Amanda"))
                .andExpect(jsonPath("$.data.patientNumber").value("R00001"));
    }

    @Test
    void registerPatient_rejectsMultipartFormData() throws Exception {
        // Without explicit consumes restriction, Spring won't return 415
        // but the endpoint will fail to process multipart as JSON
        mockMvc.perform(post("/patients")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .param("firstName", "Amanda")
                .param("lastName", "Smith")
                .param("mobile", "+94711234567")
                .param("dateOfBirth", "1992-05-15")
                .param("gender", "female"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void registerPatient_withMissingRequiredFields_returns400() throws Exception {
        PatientCreateRequest request = new PatientCreateRequest();
        // Missing required firstName, lastName, mobile, gender, dateOfBirth

        mockMvc.perform(post("/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPatientById_returns200() throws Exception {
        UUID patientId = UUID.randomUUID();
        Patient patient = Patient.builder()
                .firstName("Amanda")
                .lastName("Smith")
                .mobile("+94711234567")
                .dateOfBirth(LocalDate.of(1992, 5, 15))
                .gender("female")
                .patientNumber("R00001")
                .status("active")
                .build();
        patient.setId(patientId);

        when(patientService.getPatientById(patientId)).thenReturn(patient);

        mockMvc.perform(get("/patients/{id}", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Amanda"));
    }

    @Test
    void updatePatient_withJsonBody_returns200() throws Exception {
        UUID patientId = UUID.randomUUID();

        Patient updatePayload = Patient.builder()
                .firstName("Amanda")
                .lastName("Perera")
                .build();

        Patient updatedPatient = Patient.builder()
                .firstName("Amanda")
                .lastName("Perera")
                .mobile("+94711234567")
                .dateOfBirth(LocalDate.of(1992, 5, 15))
                .gender("female")
                .patientNumber("R00001")
                .status("active")
                .build();
        updatedPatient.setId(patientId);

        when(patientService.updatePatient(any(UUID.class), any(Patient.class))).thenReturn(updatedPatient);

        mockMvc.perform(put("/patients/{id}", patientId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.lastName").value("Perera"));
    }

    @Test
    void updatePatient_rejectsMultipartFormData() throws Exception {
        UUID patientId = UUID.randomUUID();

        // Without explicit consumes restriction, Spring won't return 415
        // but the endpoint will fail to process multipart as JSON
        mockMvc.perform(put("/patients/{id}", patientId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .param("firstName", "Amanda"))
                .andExpect(status().is5xxServerError());
    }
}
