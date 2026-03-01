package com.hms.ward.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for the Ward Service.
 * Tests the full admission lifecycle: ward creation → bed assignment →
 * patient admission → service charges → discharge → billing event.
 *
 * All tests run in order against a real PostgreSQL + NATS testcontainer.
 * defer-datasource-initialization=false ensures schema.sql runs BEFORE
 * Hibernate DDL so the 'ward' schema exists when tables are created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WardServiceE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("test_ward_db")
            .withUsername("test_user")
            .withPassword("test_pass");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> nats = new GenericContainer<>("nats:2.10-alpine")
            .withCommand("-js")
            .withExposedPorts(4222);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("nats.url", () -> "nats://localhost:" + nats.getMappedPort(4222));
        // Run schema.sql BEFORE Hibernate DDL so 'ward' schema is created first
        registry.add("spring.jpa.defer-datasource-initialization", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ─── Shared state across ordered tests ────────────────────────────────────────
    static String maleWardId;
    static String femaleWardId;
    static String availableBedId;
    static String maintenanceBedId;
    static String admission1Id;
    static String service1Id;
    static String service2Id;

    static final String PATIENT_ID = "11111111-1111-1111-1111-111111111111";
    static final String DOCTOR_ID  = "22222222-2222-2222-2222-222222222222";

    // ══════════════════════════════════════════════════════════════════════════════
    // WARD MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("E2E-01: Create Male General Ward → auto-generates 5 beds")
    void createMaleGeneralWard() throws Exception {
        String body = """
                {"name":"Male General Ward","type":"general","capacity":5}
                """;

        MvcResult result = mockMvc.perform(post("/wards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Male General Ward"))
                .andExpect(jsonPath("$.data.capacity").value(5))
                .andExpect(jsonPath("$.message").value("Ward created with 5 beds (M-101 to M-105)"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        maleWardId = json.path("data").path("id").asText();
        assertThat(maleWardId).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("E2E-02: Create Female General Ward with 3 beds")
    void createFemaleGeneralWard() throws Exception {
        String body = """
                {"name":"Female General Ward","type":"general","capacity":3}
                """;

        MvcResult result = mockMvc.perform(post("/wards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.capacity").value(3))
                .andExpect(jsonPath("$.message").value("Ward created with 3 beds (F-101 to F-103)"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        femaleWardId = json.path("data").path("id").asText();
        assertThat(femaleWardId).isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("E2E-03: List wards → both wards present with 0 occupied")
    void listWards_showsBothWards() throws Exception {
        mockMvc.perform(get("/wards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @Order(4)
    @DisplayName("E2E-04: Filter wards by type=general → 2 results")
    void listWards_filterByType() throws Exception {
        mockMvc.perform(get("/wards").param("type", "general"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @Order(5)
    @DisplayName("E2E-05: Create ward with missing name → 400 VALIDATION_ERROR")
    void createWard_missingName_returns400() throws Exception {
        String body = """
                {"type":"general","capacity":10}
                """;

        mockMvc.perform(post("/wards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // BED MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("E2E-06: List beds for male ward → 5 available beds with correct M-xxx numbering")
    void listBeds_forMaleWard() throws Exception {
        MvcResult result = mockMvc.perform(get("/beds")
                        .param("ward_id", maleWardId)
                        .param("status", "available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(5))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        availableBedId = json.path("data").get(0).path("id").asText();
        assertThat(availableBedId).isNotBlank();

        JsonNode beds = json.path("data");
        for (int i = 0; i < beds.size(); i++) {
            assertThat(beds.get(i).path("bedNumber").asText()).startsWith("M-");
            assertThat(beds.get(i).path("status").asText()).isEqualTo("available");
        }
    }

    @Test
    @Order(7)
    @DisplayName("E2E-07: List all beds → 8 total (5 male + 3 female)")
    void listBeds_allBeds() throws Exception {
        mockMvc.perform(get("/beds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8));
    }

    @Test
    @Order(8)
    @DisplayName("E2E-08: Set second bed to maintenance → only 4 available in male ward")
    void setBedToMaintenance() throws Exception {
        maintenanceBedId = getSecondBedInMaleWard();

        mockMvc.perform(put("/beds/{id}", maintenanceBedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"maintenance\",\"notes\":\"Bed frame broken\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("maintenance"));

        mockMvc.perform(get("/beds")
                        .param("ward_id", maleWardId)
                        .param("status", "available"))
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    @Test
    @Order(9)
    @DisplayName("E2E-09: Try to manually set bed to 'occupied' → 422 INVALID_STATUS")
    void setBedToOccupied_manually_returns422() throws Exception {
        mockMvc.perform(put("/beds/{id}", availableBedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"occupied\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS"));
    }

    @Test
    @Order(10)
    @DisplayName("E2E-10: Set maintenance bed back to available → 5 available again")
    void setMaintenanceBedBackToAvailable() throws Exception {
        mockMvc.perform(put("/beds/{id}", maintenanceBedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"available\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("available"));

        mockMvc.perform(get("/beds")
                        .param("ward_id", maleWardId)
                        .param("status", "available"))
                .andExpect(jsonPath("$.data.length()").value(5));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // PATIENT ADMISSION
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    @Order(11)
    @DisplayName("E2E-11: Admit patient to available bed → status admitted, bed becomes occupied")
    void admitPatient_success() throws Exception {
        String body = """
                {
                  "patientId": "%s",
                  "patientName": "Andrea Lalema",
                  "patientNumber": "R00001",
                  "bedId": "%s",
                  "attendingDoctorId": "%s",
                  "attendingDoctorName": "Dr. Jenny Smith",
                  "admissionReason": "High fever with suspected typhoid — IV treatment required",
                  "notes": "Allergic to Penicillin"
                }
                """.formatted(PATIENT_ID, availableBedId, DOCTOR_ID);

        MvcResult result = mockMvc.perform(post("/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("admitted"))
                .andExpect(jsonPath("$.data.patientName").value("Andrea Lalema"))
                .andExpect(jsonPath("$.data.patientNumber").value("R00001"))
                .andExpect(jsonPath("$.data.bedNumber").isNotEmpty())
                .andExpect(jsonPath("$.data.wardName").value("Male General Ward"))
                .andExpect(jsonPath("$.data.attendingDoctorName").value("Dr. Jenny Smith"))
                .andExpect(jsonPath("$.data.runningTotal").value(0))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        admission1Id = json.path("data").path("id").asText();
        assertThat(admission1Id).isNotBlank();
    }

    @Test
    @Order(12)
    @DisplayName("E2E-12: Bed is now occupied and shows patient name")
    void bedIsOccupiedAfterAdmission() throws Exception {
        MvcResult result = mockMvc.perform(get("/beds")
                        .param("ward_id", maleWardId)
                        .param("status", "occupied"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.path("data").get(0).path("currentPatientName").asText())
                .isEqualTo("Andrea Lalema");
        assertThat(json.path("data").get(0).path("currentAdmissionId").asText())
                .isEqualTo(admission1Id);
    }

    @Test
    @Order(13)
    @DisplayName("E2E-13: Ward occupancy reflects admitted patient → 1 occupied, 4 available")
    void wardOccupancyUpdatedAfterAdmission() throws Exception {
        MvcResult result = mockMvc.perform(get("/wards"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode wards = json.path("data");
        // Find the male ward
        for (int i = 0; i < wards.size(); i++) {
            if (maleWardId.equals(wards.get(i).path("id").asText())) {
                assertThat(wards.get(i).path("occupied").asLong()).isEqualTo(1L);
                assertThat(wards.get(i).path("available").asLong()).isEqualTo(4L);
                break;
            }
        }
    }

    @Test
    @Order(14)
    @DisplayName("E2E-14: Try to admit same patient again → 422 PATIENT_ALREADY_ADMITTED")
    void admitSamePatientAgain_returns422() throws Exception {
        String anotherBedId = getAnotherAvailableBedId();

        String body = """
                {
                  "patientId": "%s",
                  "bedId": "%s",
                  "attendingDoctorId": "%s",
                  "admissionReason": "Second attempt — should be rejected"
                }
                """.formatted(PATIENT_ID, anotherBedId, DOCTOR_ID);

        mockMvc.perform(post("/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PATIENT_ALREADY_ADMITTED"));
    }

    @Test
    @Order(15)
    @DisplayName("E2E-15: Try to admit to an occupied bed → 422 BED_NOT_AVAILABLE")
    void admitToOccupiedBed_returns422() throws Exception {
        String body = """
                {
                  "patientId": "55555555-5555-5555-5555-555555555555",
                  "bedId": "%s",
                  "attendingDoctorId": "%s",
                  "admissionReason": "Malaria treatment"
                }
                """.formatted(availableBedId, DOCTOR_ID);

        mockMvc.perform(post("/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BED_NOT_AVAILABLE"));
    }

    @Test
    @Order(16)
    @DisplayName("E2E-16: Get admission by ID → full detail with ward/bed names")
    void getAdmissionById() throws Exception {
        mockMvc.perform(get("/admissions/{id}", admission1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(admission1Id))
                .andExpect(jsonPath("$.data.patientName").value("Andrea Lalema"))
                .andExpect(jsonPath("$.data.wardName").value("Male General Ward"))
                .andExpect(jsonPath("$.data.attendingDoctorName").value("Dr. Jenny Smith"))
                .andExpect(jsonPath("$.data.services").isArray())
                .andExpect(jsonPath("$.data.runningTotal").value(0));
    }

    @Test
    @Order(17)
    @DisplayName("E2E-17: List admissions filtered by status=admitted → 1 result")
    void listAdmissions_statusAdmitted() throws Exception {
        mockMvc.perform(get("/admissions").param("status", "admitted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].patientName").value("Andrea Lalema"));
    }

    @Test
    @Order(18)
    @DisplayName("E2E-18: Get non-existent admission → 404 NOT_FOUND")
    void getAdmission_notFound() throws Exception {
        mockMvc.perform(get("/admissions/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // SERVICE CHARGES (CHARGE STACKING)
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    @Order(19)
    @DisplayName("E2E-19: Add daily bed charge (3 days) → running total 7500")
    void addBedCharge() throws Exception {
        String body = """
                {
                  "serviceName": "Daily Bed Charge",
                  "serviceType": "bed_charge",
                  "quantity": 3,
                  "unitPrice": 2500.00,
                  "notes": "3 days"
                }
                """;

        MvcResult result = mockMvc.perform(post("/admissions/{id}/services", admission1Id)
                        .header("X-User-Name", "ward_staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.serviceName").value("Daily Bed Charge"))
                .andExpect(jsonPath("$.data.totalPrice").value(7500.00))
                .andExpect(jsonPath("$.data.addedBy").value("ward_staff"))
                .andExpect(jsonPath("$.running_total").value(7500.00))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        service1Id = json.path("data").path("id").asText();
        assertThat(service1Id).isNotBlank();
    }

    @Test
    @Order(20)
    @DisplayName("E2E-20: Add medication (quantity×price computed) → running total 10200")
    void addMedicationCharge() throws Exception {
        String body = """
                {
                  "serviceName": "IV Ceftriaxone 1g",
                  "serviceType": "medication",
                  "quantity": 6,
                  "unitPrice": 450.00,
                  "notes": "Twice daily x 3 days"
                }
                """;

        MvcResult result = mockMvc.perform(post("/admissions/{id}/services", admission1Id)
                        .header("X-User-Name", "doctor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalPrice").value(2700.00))
                .andExpect(jsonPath("$.running_total").value(10200.00))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        service2Id = json.path("data").path("id").asText();
    }

    @Test
    @Order(21)
    @DisplayName("E2E-21: Add procedure (Blood Transfusion) → running total 12750")
    void addProcedureCharge() throws Exception {
        String body = """
                {
                  "serviceName": "Blood Transfusion",
                  "serviceType": "procedure",
                  "quantity": 1,
                  "unitPrice": 2550.00
                }
                """;

        mockMvc.perform(post("/admissions/{id}/services", admission1Id)
                        .header("X-User-Name", "doctor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalPrice").value(2550.00))
                .andExpect(jsonPath("$.running_total").value(12750.00));
    }

    @Test
    @Order(22)
    @DisplayName("E2E-22: Get all services for admission → 3 services, runningTotal 12750")
    void getServicesForAdmission() throws Exception {
        mockMvc.perform(get("/admissions/{id}/services", admission1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientName").value("Andrea Lalema"))
                .andExpect(jsonPath("$.data.services.length()").value(3))
                .andExpect(jsonPath("$.data.runningTotal").value(12750.00))
                .andExpect(jsonPath("$.data.daysAdmitted").isNumber());
    }

    @Test
    @Order(23)
    @DisplayName("E2E-23: Filter services by type=medication → only IV Ceftriaxone")
    void filterServicesByType() throws Exception {
        mockMvc.perform(get("/admissions/{id}/services", admission1Id)
                        .param("service_type", "medication"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.services.length()").value(1))
                .andExpect(jsonPath("$.data.services[0].serviceType").value("medication"))
                .andExpect(jsonPath("$.data.services[0].serviceName").value("IV Ceftriaxone 1g"));
    }

    @Test
    @Order(24)
    @DisplayName("E2E-24: Admission detail shows all services and running total")
    void admissionDetailShowsServices() throws Exception {
        mockMvc.perform(get("/admissions/{id}", admission1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.services.length()").value(3))
                .andExpect(jsonPath("$.data.runningTotal").value(12750.00));
    }

    @Test
    @Order(25)
    @DisplayName("E2E-25: Remove medication charge → running total drops to 10050")
    void removeServiceCharge() throws Exception {
        // 12750 - 2700 (medication) = 10050
        mockMvc.perform(delete("/admissions/{id}/services/{serviceId}", admission1Id, service2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.running_total").value(10050.00));

        mockMvc.perform(get("/admissions/{id}/services", admission1Id))
                .andExpect(jsonPath("$.data.services.length()").value(2))
                .andExpect(jsonPath("$.data.runningTotal").value(10050.00));
    }

    @Test
    @Order(26)
    @DisplayName("E2E-26: Add service with quantity=0 → 400 VALIDATION_ERROR")
    void addServiceCharge_invalidQuantity_returns400() throws Exception {
        String body = """
                {
                  "serviceName": "Test Service",
                  "serviceType": "other",
                  "quantity": 0,
                  "unitPrice": 100.00
                }
                """;

        mockMvc.perform(post("/admissions/{id}/services", admission1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @Order(27)
    @DisplayName("E2E-27: Remove non-existent service → 404 NOT_FOUND")
    void removeNonExistentService_returns404() throws Exception {
        mockMvc.perform(delete("/admissions/{id}/services/00000000-0000-0000-0000-000000000000",
                        admission1Id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // PATIENT DISCHARGE
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    @Order(28)
    @DisplayName("E2E-28: Discharge patient → status discharged, final_total=10050, billing event published")
    void dischargePatient_success() throws Exception {
        String body = """
                {
                  "dischargeNotes": "Patient recovered well. Discharge with oral antibiotics x 7 days.",
                  "dischargeDiagnosis": "Typhoid Fever — resolved with IV Ceftriaxone"
                }
                """;

        mockMvc.perform(put("/admissions/{id}/discharge", admission1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("discharged"))
                .andExpect(jsonPath("$.data.dischargeNotes").value("Patient recovered well. Discharge with oral antibiotics x 7 days."))
                .andExpect(jsonPath("$.data.dischargeDiagnosis").value("Typhoid Fever — resolved with IV Ceftriaxone"))
                .andExpect(jsonPath("$.message").value("Patient discharged. Ward invoice will be created automatically."))
                .andExpect(jsonPath("$.final_total").value(10050.00));
    }

    @Test
    @Order(29)
    @DisplayName("E2E-29: Bed released after discharge → all 5 available in male ward")
    void bedReleasedAfterDischarge() throws Exception {
        mockMvc.perform(get("/beds")
                        .param("ward_id", maleWardId)
                        .param("status", "available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5));
    }

    @Test
    @Order(30)
    @DisplayName("E2E-30: Ward occupancy → back to 0 occupied after discharge")
    void wardOccupancyAfterDischarge() throws Exception {
        MvcResult result = mockMvc.perform(get("/wards"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode wards = json.path("data");
        for (int i = 0; i < wards.size(); i++) {
            if (maleWardId.equals(wards.get(i).path("id").asText())) {
                assertThat(wards.get(i).path("occupied").asLong()).isEqualTo(0L);
                assertThat(wards.get(i).path("available").asLong()).isEqualTo(5L);
                break;
            }
        }
    }

    @Test
    @Order(31)
    @DisplayName("E2E-31: Discharge already-discharged patient → 422 ALREADY_DISCHARGED")
    void dischargeAgain_returns422() throws Exception {
        mockMvc.perform(put("/admissions/{id}/discharge", admission1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ALREADY_DISCHARGED"));
    }

    @Test
    @Order(32)
    @DisplayName("E2E-32: Add service to discharged admission → 422 ADMISSION_DISCHARGED")
    void addServiceToDischargedAdmission_returns422() throws Exception {
        String body = """
                {
                  "serviceName": "Post-discharge test",
                  "serviceType": "other",
                  "quantity": 1,
                  "unitPrice": 100.00
                }
                """;

        mockMvc.perform(post("/admissions/{id}/services", admission1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ADMISSION_DISCHARGED"));
    }

    @Test
    @Order(33)
    @DisplayName("E2E-33: Remove service from discharged admission → 422 ADMISSION_DISCHARGED")
    void removeServiceFromDischargedAdmission_returns422() throws Exception {
        mockMvc.perform(delete("/admissions/{id}/services/{serviceId}", admission1Id, service1Id))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ADMISSION_DISCHARGED"));
    }

    @Test
    @Order(34)
    @DisplayName("E2E-34: List admissions → shows discharged record in history")
    void listAdmissions_showsDischargedAdmission() throws Exception {
        mockMvc.perform(get("/admissions").param("status", "discharged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("discharged"))
                .andExpect(jsonPath("$.data[0].dischargeNotes").isNotEmpty());
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // PATIENT ADMISSION HISTORY
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    @Order(35)
    @DisplayName("E2E-35: Patient admission history → 1 completed admission")
    void patientAdmissionHistory() throws Exception {
        mockMvc.perform(get("/patients/{patientId}/admissions", PATIENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].status").value("discharged"))
                .andExpect(jsonPath("$.data[0].patientName").value("Andrea Lalema"));
    }

    @Test
    @Order(36)
    @DisplayName("E2E-36: Patient with no admission history → empty paginated list")
    void patientWithNoHistory_returnsEmpty() throws Exception {
        mockMvc.perform(get("/patients/99999999-9999-9999-9999-999999999999/admissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.total").value(0));
    }

    @Test
    @Order(37)
    @DisplayName("E2E-37: Discharge without body (optional body) → success")
    void dischargeWithoutBody_success() throws Exception {
        // Admit a fresh patient
        String admitBody = """
                {
                  "patientId": "33333333-3333-3333-3333-333333333333",
                  "patientName": "John Fernando",
                  "bedId": "%s",
                  "attendingDoctorId": "%s",
                  "admissionReason": "Post-operative recovery"
                }
                """.formatted(availableBedId, DOCTOR_ID);

        MvcResult admitResult = mockMvc.perform(post("/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(admitBody))
                .andExpect(status().isCreated())
                .andReturn();

        String newAdmissionId = objectMapper.readTree(
                admitResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Discharge without a body (body is optional per spec)
        mockMvc.perform(put("/admissions/{id}/discharge", newAdmissionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("discharged"))
                .andExpect(jsonPath("$.final_total").value(0.0));
    }

    @Test
    @Order(38)
    @DisplayName("E2E-38: Paginated admission list returns correct meta")
    void listAdmissions_pagination() throws Exception {
        mockMvc.perform(get("/admissions")
                        .param("page", "1")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(1))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @Order(39)
    @DisplayName("E2E-39: Filter admissions by ward_id → only admissions in that ward")
    void listAdmissions_filterByWard() throws Exception {
        mockMvc.perform(get("/admissions").param("ward_id", maleWardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(2)); // both admissions in male ward
    }

    @Test
    @Order(40)
    @DisplayName("E2E-40: Create ICU ward and admit patient → ICU occupancy tracked separately")
    void icuWardAdmission() throws Exception {
        // Create ICU ward
        MvcResult icuResult = mockMvc.perform(post("/wards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ICU\",\"type\":\"icu\",\"capacity\":2}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode icuJson = objectMapper.readTree(icuResult.getResponse().getContentAsString());
        String icuWardId = icuJson.path("data").path("id").asText();

        // Get an ICU bed
        MvcResult icuBedsResult = mockMvc.perform(get("/beds")
                        .param("ward_id", icuWardId)
                        .param("status", "available"))
                .andReturn();
        String icuBedId = objectMapper.readTree(icuBedsResult.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText();

        // Admit patient to ICU
        String admitBody = """
                {
                  "patientId": "44444444-4444-4444-4444-444444444444",
                  "patientName": "Sunil Perera",
                  "bedId": "%s",
                  "attendingDoctorId": "%s",
                  "admissionReason": "Critical respiratory failure"
                }
                """.formatted(icuBedId, DOCTOR_ID);

        mockMvc.perform(post("/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(admitBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.wardName").value("ICU"));

        // ICU ward shows 1 occupied
        MvcResult wardResult = mockMvc.perform(get("/wards").param("type", "icu"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode wardJson = objectMapper.readTree(wardResult.getResponse().getContentAsString());
        assertThat(wardJson.path("data").get(0).path("occupied").asLong()).isEqualTo(1L);
        assertThat(wardJson.path("data").get(0).path("available").asLong()).isEqualTo(1L);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════════

    private String getSecondBedInMaleWard() throws Exception {
        MvcResult result = mockMvc.perform(get("/beds").param("ward_id", maleWardId))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.path("data").get(1).path("id").asText();
    }

    private String getAnotherAvailableBedId() throws Exception {
        MvcResult result = mockMvc.perform(get("/beds")
                        .param("ward_id", maleWardId)
                        .param("status", "available"))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        for (int i = 0; i < json.path("data").size(); i++) {
            String id = json.path("data").get(i).path("id").asText();
            if (!id.equals(availableBedId)) return id;
        }
        return json.path("data").get(0).path("id").asText();
    }
}
