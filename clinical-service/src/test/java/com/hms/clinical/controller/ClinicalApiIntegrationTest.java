package com.hms.clinical.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.clinical.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClinicalApiIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("hms_db")
            .withUsername("hms_user")
            .withPassword("hms_password");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> nats = new GenericContainer<>("nats:2.10-alpine")
            .withCommand("-js")
            .withExposedPorts(4222);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("nats.url", () -> "nats://localhost:" + nats.getMappedPort(4222));
        // Disable MinIO for integration tests (images won't be tested against real MinIO)
        registry.add("minio.url", () -> "http://localhost:9000");
        registry.add("minio.accessKey", () -> "minioadmin");
        registry.add("minio.secretKey", () -> "minioadmin");
        registry.add("minio.bucketName", () -> "hms-scans");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String sessionId;
    private static String prescriptionId;

    // ───────── SESSION TESTS ─────────

    @Test
    @Order(1)
    void createSession_shouldReturn201() throws Exception {
        SessionCreateRequest request = SessionCreateRequest.builder()
                .patientId(UUID.randomUUID())
                .sessionType("opd")
                .chiefComplaint("Persistent fever for 3 days")
                .build();

        MvcResult result = mockMvc.perform(post("/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Name", "Dr. Jenny Smith")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.sessionType").value("opd"))
                .andExpect(jsonPath("$.data.status").value("open"))
                .andExpect(jsonPath("$.data.chiefComplaint").value("Persistent fever for 3 days"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        sessionId = objectMapper.readTree(body).get("data").get("id").asText();
    }

    @Test
    @Order(2)
    void listSessions_shouldReturn200() throws Exception {
        mockMvc.perform(get("/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(3)
    void getSession_shouldReturn200() throws Exception {
        mockMvc.perform(get("/sessions/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sessionId))
                .andExpect(jsonPath("$.data.status").value("open"));
    }

    @Test
    @Order(4)
    void getSession_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/sessions/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @Order(5)
    void updateSession_shouldReturn200() throws Exception {
        SessionUpdateRequest request = SessionUpdateRequest.builder()
                .chiefComplaint("Updated complaint")
                .diagnosis("Suspected Typhoid")
                .build();

        mockMvc.perform(put("/sessions/{id}", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.chiefComplaint").value("Updated complaint"))
                .andExpect(jsonPath("$.data.diagnosis").value("Suspected Typhoid"));
    }

    // ───────── VITALS TESTS ─────────

    @Test
    @Order(10)
    void recordVitals_shouldReturn201() throws Exception {
        VitalsCreateRequest request = VitalsCreateRequest.builder()
                .bpm(80)
                .temperature(37.8)
                .bloodPressureSys(120)
                .bloodPressureDia(80)
                .spo2(98)
                .weightKg(68.5)
                .heightCm(170)
                .build();

        mockMvc.perform(post("/sessions/{id}/vitals", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bpm").value(80))
                .andExpect(jsonPath("$.data.temperature").value(37.8))
                .andExpect(jsonPath("$.data.spo2").value(98));
    }

    @Test
    @Order(11)
    void getVitals_shouldReturn200() throws Exception {
        mockMvc.perform(get("/sessions/{id}/vitals", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bpm").value(80));
    }

    @Test
    @Order(12)
    void recordVitals_update_shouldOverwrite() throws Exception {
        VitalsCreateRequest request = VitalsCreateRequest.builder()
                .bpm(90)
                .temperature(38.2)
                .build();

        mockMvc.perform(post("/sessions/{id}/vitals", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.bpm").value(90))
                .andExpect(jsonPath("$.data.temperature").value(38.2));
    }

    // ───────── PRESCRIPTION TESTS ─────────

    @Test
    @Order(20)
    void addPrescriptions_shouldReturn201() throws Exception {
        PrescriptionCreateRequest request = PrescriptionCreateRequest.builder()
                .prescriptions(List.of(
                        PrescriptionCreateRequest.PrescriptionItem.builder()
                                .type("internal")
                                .medicineName("Paracetamol")
                                .dosage("500mg")
                                .frequency("Every 8 hours")
                                .durationDays(5)
                                .instructions("Take with water")
                                .build(),
                        PrescriptionCreateRequest.PrescriptionItem.builder()
                                .type("external")
                                .medicineName("Amoxicillin")
                                .dosage("500mg")
                                .frequency("Three times daily")
                                .durationDays(7)
                                .pharmacyName("Narammala Pharmacy")
                                .build()))
                .build();

        MvcResult result = mockMvc.perform(post("/sessions/{id}/prescriptions", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].medicineName").value("Paracetamol"))
                .andExpect(jsonPath("$.data[0].status").value("pending"))
                .andExpect(jsonPath("$.data[1].medicineName").value("Amoxicillin"))
                .andExpect(jsonPath("$.data[1].status").value("dispensed"))
                .andReturn();

        // Save first prescription ID for later tests
        String body = result.getResponse().getContentAsString();
        prescriptionId = objectMapper.readTree(body).get("data").get(0).get("id").asText();
    }

    @Test
    @Order(21)
    void getPrescriptions_shouldReturn200() throws Exception {
        mockMvc.perform(get("/sessions/{id}/prescriptions", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @Order(22)
    void getPrescriptions_filterByType_shouldReturn200() throws Exception {
        mockMvc.perform(get("/sessions/{id}/prescriptions", sessionId)
                .param("type", "internal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("internal"));
    }

    // ───────── PHARMACY TESTS ─────────

    @Test
    @Order(30)
    void getPharmacyQueue_shouldReturn200() throws Exception {
        mockMvc.perform(get("/pharmacy/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(31)
    void dispensePrescription_shouldReturn200() throws Exception {
        mockMvc.perform(put("/pharmacy/{rxId}/dispense", prescriptionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("dispensed"));
    }

    @Test
    @Order(32)
    void dispensePrescription_alreadyDispensed_shouldReturn422() throws Exception {
        mockMvc.perform(put("/pharmacy/{rxId}/dispense", prescriptionId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNPROCESSABLE_ENTITY"));
    }

    // ───────── LAB REQUEST TESTS ─────────

    @Test
    @Order(40)
    void createLabRequests_shouldReturn201() throws Exception {
        LabRequestCreateRequest request = LabRequestCreateRequest.builder()
                .tests(List.of(
                        LabRequestCreateRequest.TestItem.builder()
                                .testId(UUID.randomUUID())
                                .urgency("routine")
                                .build(),
                        LabRequestCreateRequest.TestItem.builder()
                                .testId(UUID.randomUUID())
                                .urgency("urgent")
                                .build()))
                .notes("Suspect typhoid")
                .build();

        mockMvc.perform(post("/sessions/{id}/lab-requests", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.message").value("Lab request sent. Lab Service will create the order."));
    }

    @Test
    @Order(41)
    void getLabRequests_shouldReturn200() throws Exception {
        mockMvc.perform(get("/sessions/{id}/lab-requests", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    // ───────── COMPLETE SESSION & POST-COMPLETE TESTS ─────────

    @Test
    @Order(50)
    void completeSession_shouldReturn200AndPublishEvent() throws Exception {
        SessionCompleteRequest request = SessionCompleteRequest.builder()
                .diagnosis("Typhoid Fever — Widal test positive")
                .discountPercent(0.0)
                .build();

        mockMvc.perform(put("/sessions/{id}/complete", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.diagnosis").value("Typhoid Fever — Widal test positive"))
                .andExpect(jsonPath("$.message").value("Session completed. Invoice will be created automatically."));
    }

    @Test
    @Order(51)
    void completeSession_alreadyCompleted_shouldReturn422() throws Exception {
        SessionCompleteRequest request = SessionCompleteRequest.builder()
                .diagnosis("Test")
                .build();

        mockMvc.perform(put("/sessions/{id}/complete", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNPROCESSABLE_ENTITY"));
    }

    @Test
    @Order(52)
    void updateCompletedSession_shouldReturn422() throws Exception {
        SessionUpdateRequest request = SessionUpdateRequest.builder()
                .chiefComplaint("Should fail")
                .build();

        mockMvc.perform(put("/sessions/{id}", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(53)
    void addPrescriptionToCompletedSession_shouldReturn422() throws Exception {
        PrescriptionCreateRequest request = PrescriptionCreateRequest.builder()
                .prescriptions(List.of(
                        PrescriptionCreateRequest.PrescriptionItem.builder()
                                .type("internal").medicineName("Test")
                                .dosage("1").frequency("1").durationDays(1)
                                .build()))
                .build();

        mockMvc.perform(post("/sessions/{id}/prescriptions", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(54)
    void recordVitalsOnCompletedSession_shouldReturn422() throws Exception {
        VitalsCreateRequest request = VitalsCreateRequest.builder().bpm(70).build();

        mockMvc.perform(post("/sessions/{id}/vitals", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ───────── PATIENT HISTORY TEST ─────────

    @Test
    @Order(60)
    void getPatientHistory_shouldReturn200() throws Exception {
        // We need to get the patient ID from the session we created
        MvcResult sessionResult = mockMvc.perform(get("/sessions/{id}", sessionId))
                .andExpect(status().isOk())
                .andReturn();

        String patientId = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                .get("data").get("patientId").asText();

        mockMvc.perform(get("/patients/{patientId}/history", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessions", hasSize(greaterThanOrEqualTo(1))));
    }

    // ───────── VALIDATION TESTS ─────────

    @Test
    @Order(70)
    void createSession_missingPatientId_shouldReturn400() throws Exception {
        SessionCreateRequest request = SessionCreateRequest.builder()
                .sessionType("opd")
                .build();

        mockMvc.perform(post("/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @Order(71)
    void createSession_missingSessionType_shouldReturn400() throws Exception {
        SessionCreateRequest request = SessionCreateRequest.builder()
                .patientId(UUID.randomUUID())
                .build();

        mockMvc.perform(post("/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(72)
    void completeSession_missingDiagnosis_shouldReturn400() throws Exception {
        // Create a new open session first
        SessionCreateRequest createReq = SessionCreateRequest.builder()
                .patientId(UUID.randomUUID())
                .sessionType("wound_care")
                .build();

        MvcResult createResult = mockMvc.perform(post("/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Name", "Dr. Test")
                .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String newSessionId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asText();

        SessionCompleteRequest completeReq = SessionCompleteRequest.builder().build();

        mockMvc.perform(put("/sessions/{id}/complete", newSessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isBadRequest());
    }
}
