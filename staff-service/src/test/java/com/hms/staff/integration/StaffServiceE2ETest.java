package com.hms.staff.integration;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.staff.dto.DepartmentCreateRequest;
import com.hms.staff.dto.DepartmentUpdateRequest;
import com.hms.staff.dto.NurseAllocationCreateRequest;
import com.hms.staff.dto.ScheduleCreateRequest;
import com.hms.staff.dto.StaffMemberCreateRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc(addFilters = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StaffServiceE2ETest {

    // ─── Containers ───────────────────────────────────────────────────────────

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("hms_db")
            .withUsername("hms_user")
            .withPassword("hms_password");

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:23.0")
            .withAdminUsername("admin")
            .withAdminPassword("admin");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("keycloak.admin.url", keycloak::getAuthServerUrl);
        registry.add("keycloak.admin.realm", () -> "hms");
        registry.add("keycloak.admin.username", () -> "admin");
        registry.add("keycloak.admin.password", () -> "admin");
        registry.add("keycloak.enabled", () -> "true");
        registry.add("minio.url", () -> "http://localhost:9999");
        registry.add("minio.accessKey", () -> "minioadmin");
        registry.add("minio.secretKey", () -> "minioadmin");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // Shared state across ordered tests
    static UUID createdDeptId;
    static UUID createdDoctorId;
    static UUID createdNurseId;
    static UUID createdScheduleId;
    static UUID createdAllocationId;

    // ─── Keycloak Setup ───────────────────────────────────────────────────────

    @BeforeAll
    static void setupKeycloak() throws Exception {
        RestTemplate rest = new RestTemplate();
        String kcUrl = keycloak.getAuthServerUrl();

        // Get master realm admin token
        String tokenUrl = kcUrl + "/realms/master/protocol/openid-connect/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", "admin-cli");
        body.add("username", "admin");
        body.add("password", "admin");

        ResponseEntity<Map> tokenResp = rest.postForEntity(tokenUrl,
                new org.springframework.http.HttpEntity<>(body, headers), Map.class);
        String adminToken = (String) tokenResp.getBody().get("access_token");

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(adminToken);
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        // Create hms realm
        String createRealmUrl = kcUrl + "/admin/realms";
        Map<String, Object> realm = Map.of(
                "realm", "hms",
                "enabled", true,
                "displayName", "HMS"
        );
        try {
            rest.postForEntity(createRealmUrl,
                    new org.springframework.http.HttpEntity<>(realm, authHeaders), Void.class);
        } catch (Exception e) {
            // Realm may already exist
        }

        // Create doctor role in hms realm
        String createRoleUrl = kcUrl + "/admin/realms/hms/roles";
        for (String role : List.of("doctor", "nurse", "receptionist")) {
            try {
                rest.postForEntity(createRoleUrl,
                        new org.springframework.http.HttpEntity<>(Map.of("name", role), authHeaders), Void.class);
            } catch (Exception e) {
                // Role may already exist
            }
        }
    }

    // ─── 1. Departments ───────────────────────────────────────────────────────

    @Test
    @Order(1)
    void createDepartment_success() throws Exception {
        DepartmentCreateRequest req = new DepartmentCreateRequest();
        req.setName("Cardiology");
        req.setDescription("Heart and cardiovascular care");

        MvcResult result = mockMvc.perform(post("/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Cardiology"))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        createdDeptId = UUID.fromString(objectMapper.readTree(json).path("data").path("id").asText());
    }

    @Test
    @Order(2)
    void listDepartments_containsCreated() throws Exception {
        mockMvc.perform(get("/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[?(@.name=='Cardiology')]").exists());
    }

    @Test
    @Order(3)
    void createDepartment_duplicateName_returns409() throws Exception {
        DepartmentCreateRequest req = new DepartmentCreateRequest();
        req.setName("Cardiology");

        mockMvc.perform(post("/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEPARTMENT_NAME_EXISTS"));
    }

    @Test
    @Order(4)
    void updateDepartment_success() throws Exception {
        DepartmentUpdateRequest req = new DepartmentUpdateRequest();
        req.setDescription("Updated: Heart and vascular care");

        mockMvc.perform(put("/departments/{id}", createdDeptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Updated: Heart and vascular care"));
    }

    // ─── 2. Doctors ───────────────────────────────────────────────────────────

    @Test
    @Order(5)
    void createDoctor_withKeycloak_success() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/doctors")
                        .file(new MockMultipartFile("first_name", "Jenny".getBytes()))
                        .file(new MockMultipartFile("last_name", "Smith".getBytes()))
                        .file(new MockMultipartFile("username", "dr.jenny.e2e".getBytes()))
                        .file(new MockMultipartFile("email", "jenny.e2e@hospital.com".getBytes()))
                        .file(new MockMultipartFile("mobile", "+94771234567".getBytes()))
                        .file(new MockMultipartFile("password", "Password@123".getBytes()))
                        .file(new MockMultipartFile("date_of_birth", "1985-05-20".getBytes()))
                        .file(new MockMultipartFile("gender", "female".getBytes()))
                        .file(new MockMultipartFile("education", "MBBS, MS".getBytes()))
                        .file(new MockMultipartFile("designation", "Senior Physician".getBytes()))
                        .file(new MockMultipartFile("department_id", createdDeptId.toString().getBytes())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Jenny"))
                .andExpect(jsonPath("$.data.keycloakUserId").isNotEmpty())
                .andExpect(jsonPath("$.message").value(containsString("dr.jenny.e2e")))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        createdDoctorId = UUID.fromString(
                objectMapper.readTree(json).path("data").path("id").asText());

        // Verify keycloak_user_id was stored
        String keycloakId = objectMapper.readTree(json).path("data").path("keycloakUserId").asText();
        assertThat(keycloakId).isNotBlank();
    }

    @Test
    @Order(6)
    void getDoctorById_returns200() throws Exception {
        mockMvc.perform(get("/doctors/{id}", createdDoctorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("dr.jenny.e2e"))
                .andExpect(jsonPath("$.data.departmentName").value("Cardiology"));
    }

    @Test
    @Order(7)
    void listDoctors_withDeptFilter_returns200() throws Exception {
        mockMvc.perform(get("/doctors").param("department_id", createdDeptId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.meta.total").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(8)
    void createDoctor_duplicateUsername_returns409() throws Exception {
        mockMvc.perform(multipart("/doctors")
                        .file(new MockMultipartFile("first_name", "Duplicate".getBytes()))
                        .file(new MockMultipartFile("last_name", "Doc".getBytes()))
                        .file(new MockMultipartFile("username", "dr.jenny.e2e".getBytes()))
                        .file(new MockMultipartFile("email", "other@hospital.com".getBytes()))
                        .file(new MockMultipartFile("mobile", "+94770000000".getBytes()))
                        .file(new MockMultipartFile("password", "Password@123".getBytes()))
                        .file(new MockMultipartFile("date_of_birth", "1990-01-01".getBytes()))
                        .file(new MockMultipartFile("gender", "male".getBytes()))
                        .file(new MockMultipartFile("education", "MBBS".getBytes()))
                        .file(new MockMultipartFile("designation", "Physician".getBytes()))
                        .file(new MockMultipartFile("department_id", createdDeptId.toString().getBytes())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USERNAME_TAKEN"));
    }

    // ─── 3. Schedules ─────────────────────────────────────────────────────────

    @Test
    @Order(9)
    void addScheduleEntry_success() throws Exception {
        ScheduleCreateRequest req = new ScheduleCreateRequest();
        req.setDayOfWeek(1); // Monday
        req.setStartTime("08:00");
        req.setEndTime("12:00");
        req.setSlotDurationMinutes(15);
        req.setMaxPatients(16);

        MvcResult result = mockMvc.perform(post("/doctors/{id}/schedule", createdDoctorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dayName").value("Monday"))
                .andExpect(jsonPath("$.data.startTime").value("08:00"))
                .andExpect(jsonPath("$.data.maxPatients").value(16))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        createdScheduleId = UUID.fromString(
                objectMapper.readTree(json).path("data").path("id").asText());
    }

    @Test
    @Order(10)
    void getDoctorSchedule_containsEntry() throws Exception {
        mockMvc.perform(get("/doctors/{id}/schedule", createdDoctorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].slotDurationMinutes").value(15));
    }

    @Test
    @Order(11)
    void getDoctorAvailability_returnsSlots() throws Exception {
        // March 2, 2026 is a Monday — matches day_of_week=1
        mockMvc.perform(get("/doctors/{id}/availability", createdDoctorId)
                        .param("date", "2026-03-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doctorName").value(containsString("Jenny")))
                .andExpect(jsonPath("$.data.slots", hasSize(16))) // 8:00-12:00, 15-min slots
                .andExpect(jsonPath("$.data.slots[0].fromTime").value("08:00"))
                .andExpect(jsonPath("$.data.slots[0].isAvailable").value(true));
    }

    @Test
    @Order(12)
    void getDoctorAvailability_noScheduleDay_returns404() throws Exception {
        // March 3, 2026 is a Tuesday — no schedule set for Tuesday
        mockMvc.perform(get("/doctors/{id}/availability", createdDoctorId)
                        .param("date", "2026-03-03"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @Order(13)
    void updateScheduleEntry_success() throws Exception {
        mockMvc.perform(put("/schedules/{id}", createdScheduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"max_patients\": 20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxPatients").value(20));
    }

    // ─── 4. Staff Members (Nurses) ────────────────────────────────────────────

    @Test
    @Order(14)
    void createStaffMember_nurse_success() throws Exception {
        StaffMemberCreateRequest req = new StaffMemberCreateRequest();
        req.setFullName("Sarah Williams");
        req.setRole("nurse");
        req.setEmail("sarah@hospital.com");
        req.setMobile("+94779876543");

        MvcResult result = mockMvc.perform(post("/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fullName").value("Sarah Williams"))
                .andExpect(jsonPath("$.data.role").value("nurse"))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        createdNurseId = UUID.fromString(
                objectMapper.readTree(json).path("data").path("id").asText());
    }

    @Test
    @Order(15)
    void listStaff_containsNurse() throws Exception {
        mockMvc.perform(get("/staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[?(@.fullName=='Sarah Williams')]").exists());
    }

    @Test
    @Order(16)
    void getAvailableStaff_returnsNurse() throws Exception {
        mockMvc.perform(get("/staff/available").param("date", "2026-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.fullName=='Sarah Williams')]").exists());
    }

    // ─── 5. Nurse Allocations ─────────────────────────────────────────────────

    @Test
    @Order(17)
    void allocateNurse_success() throws Exception {
        NurseAllocationCreateRequest req = new NurseAllocationCreateRequest();
        req.setNurseId(createdNurseId);
        req.setDoctorId(createdDoctorId);
        req.setSessionDate(LocalDate.of(2026, 3, 10));

        MvcResult result = mockMvc.perform(post("/nurse-allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("X-User-Name", "admin"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nurseName").value("Sarah Williams"))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        createdAllocationId = UUID.fromString(
                objectMapper.readTree(json).path("data").path("id").asText());
    }

    @Test
    @Order(18)
    void allocateNurse_alreadyAllocated_returns422() throws Exception {
        NurseAllocationCreateRequest req = new NurseAllocationCreateRequest();
        req.setNurseId(createdNurseId);
        req.setDoctorId(createdDoctorId);
        req.setSessionDate(LocalDate.of(2026, 3, 10)); // same date

        mockMvc.perform(post("/nurse-allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("NURSE_ALREADY_ALLOCATED"))
                .andExpect(jsonPath("$.error.message").value(containsString("Sarah Williams")));
    }

    @Test
    @Order(19)
    void getAvailableStaff_nurseAllocated_notInList() throws Exception {
        // Sarah is now allocated on 2026-03-10, so should NOT appear as available
        mockMvc.perform(get("/staff/available").param("date", "2026-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.fullName=='Sarah Williams')]").doesNotExist());
    }

    @Test
    @Order(20)
    void listAllocations_withFilters() throws Exception {
        mockMvc.perform(get("/nurse-allocations")
                        .param("doctor_id", createdDoctorId.toString())
                        .param("status", "active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].nurseName").value("Sarah Williams"));
    }

    @Test
    @Order(21)
    void completeAllocation_success() throws Exception {
        mockMvc.perform(put("/nurse-allocations/{id}/complete", createdAllocationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("completed"));
    }

    @Test
    @Order(22)
    void completeAllocation_alreadyCompleted_returns422() throws Exception {
        mockMvc.perform(put("/nurse-allocations/{id}/complete", createdAllocationId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ALLOCATION_NOT_ACTIVE"));
    }

    @Test
    @Order(23)
    void getAvailableStaff_afterComplete_nurseIsAvailableAgain() throws Exception {
        // Nurse is completed → should be available again on same date
        mockMvc.perform(get("/staff/available").param("date", "2026-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.fullName=='Sarah Williams')]").exists());
    }

    // ─── 6. Department Deletion ───────────────────────────────────────────────

    @Test
    @Order(24)
    void deleteDepartment_withActiveDoctor_returns422() throws Exception {
        mockMvc.perform(delete("/departments/{id}", createdDeptId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("DEPARTMENT_HAS_ACTIVE_DOCTORS"));
    }

    // ─── 7. Doctor Deactivation ───────────────────────────────────────────────

    @Test
    @Order(25)
    void deactivateDoctor_success_disablesKeycloak() throws Exception {
        mockMvc.perform(delete("/doctors/{id}", createdDoctorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Doctor deactivated. Keycloak account disabled."));

        // Verify doctor is now inactive
        mockMvc.perform(get("/doctors/{id}", createdDoctorId))
                .andExpect(jsonPath("$.data.status").value("inactive"));
    }

    @Test
    @Order(26)
    void deleteDepartment_afterDoctorDeactivated_success() throws Exception {
        mockMvc.perform(delete("/departments/{id}", createdDeptId))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(27)
    void deleteScheduleEntry_success() throws Exception {
        mockMvc.perform(delete("/schedules/{id}", createdScheduleId))
                .andExpect(status().isNoContent());
    }
}
