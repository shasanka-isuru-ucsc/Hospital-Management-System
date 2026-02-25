package com.hms.staff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.staff.dto.DepartmentDto;
import com.hms.staff.dto.DoctorCreateRequest;
import com.hms.staff.entity.Department;
import com.hms.staff.repository.DepartmentRepository;
import com.hms.staff.service.KeycloakService;
import com.hms.staff.service.MinioService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StaffServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("hms_db")
            .withUsername("hms_user")
            .withPassword("hms_password")
            .withInitScript("schema.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.sql.init.mode", () -> "always");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DepartmentRepository departmentRepository;

    @MockBean
    private KeycloakService keycloakService;

    @MockBean
    private MinioService minioService;

    private static UUID departmentId;
    private static UUID doctorId;

    @Test
    @Order(1)
    void testCreateDepartment() throws Exception {
        DepartmentDto request = DepartmentDto.builder()
                .name("Cardiology")
                .description("Heart specialists")
                .build();

        mockMvc.perform(post("/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Cardiology"))
                .andDo(result -> {
                    String body = result.getResponse().getContentAsString();
                    departmentId = UUID.fromString(objectMapper.readTree(body).get("data").get("id").asText());
                });
    }

    @Test
    @Order(2)
    void testCreateDoctor() throws Exception {
        DoctorCreateRequest request = DoctorCreateRequest.builder()
                .firstName("Jenny")
                .lastName("Smith")
                .username("dr.jennysmith")
                .email("jenny.smith@hms.com")
                .mobile("+94771234567")
                .password("Password123!")
                .dateOfBirth(LocalDate.of(1985, 6, 15))
                .gender("female")
                .education("MBBS, MD")
                .designation("Senior Physician")
                .departmentId(departmentId)
                .status("active")
                .build();

        when(keycloakService.createUser(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(UUID.randomUUID().toString());
        when(minioService.uploadFile(any(), anyString())).thenReturn("mock-url");
        when(minioService.getPresignedUrl(anyString(), anyString())).thenReturn("http://mock-minio/avatar.jpg");

        MockMultipartFile dataFile = new MockMultipartFile("data", "", "application/json",
                objectMapper.writeValueAsBytes(request));

        mockMvc.perform(multipart("/doctors")
                .file(dataFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Jenny"))
                .andDo(result -> {
                    String body = result.getResponse().getContentAsString();
                    doctorId = UUID.fromString(objectMapper.readTree(body).get("data").get("id").asText());
                });
    }

    @Test
    @Order(3)
    void testGetDoctors() throws Exception {
        mockMvc.perform(get("/doctors")
                .param("page", "1")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(4)
    void testGetDoctorById() throws Exception {
        mockMvc.perform(get("/doctors/{id}", doctorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(doctorId.toString()));
    }
}
