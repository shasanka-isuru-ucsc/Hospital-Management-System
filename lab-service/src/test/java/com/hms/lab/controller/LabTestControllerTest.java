package com.hms.lab.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.lab.dto.LabTestCreateRequest;
import com.hms.lab.dto.LabTestDto;
import com.hms.lab.dto.LabTestUpdateRequest;
import com.hms.lab.exception.BusinessException;
import com.hms.lab.exception.ResourceNotFoundException;
import com.hms.lab.service.LabTestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LabTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class LabTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LabTestService labTestService;

    private LabTestDto buildSampleTest() {
        return LabTestDto.builder()
                .id(UUID.randomUUID())
                .name("Complete Blood Count")
                .code("CBC")
                .category("Haematology")
                .unitPrice(new BigDecimal("750.00"))
                .turnaroundHours(4)
                .referenceRange("WBC: 4.5-11.0 x10³/μL")
                .isActive(true)
                .build();
    }

    // ─── GET /tests ──────────────────────────────────────────────────────────────

    @Test
    void getTestCatalog_returnsListOfTests() throws Exception {
        when(labTestService.getTestCatalog(any(), any(), any()))
                .thenReturn(List.of(buildSampleTest()));

        mockMvc.perform(get("/tests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].code").value("CBC"))
                .andExpect(jsonPath("$.data[0].name").value("Complete Blood Count"));
    }

    @Test
    void getTestCatalog_withCategoryFilter_passesFilterToService() throws Exception {
        when(labTestService.getTestCatalog(eq("Haematology"), any(), any()))
                .thenReturn(List.of(buildSampleTest()));

        mockMvc.perform(get("/tests").param("category", "Haematology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].category").value("Haematology"));
    }

    // ─── POST /tests ─────────────────────────────────────────────────────────────

    @Test
    void addTest_withValidRequest_returns201() throws Exception {
        LabTestDto created = buildSampleTest();
        when(labTestService.addTest(any(LabTestCreateRequest.class))).thenReturn(created);

        String requestBody = """
                {
                  "name": "Complete Blood Count",
                  "code": "CBC",
                  "category": "Haematology",
                  "unitPrice": 750.00,
                  "turnaroundHours": 4,
                  "referenceRange": "WBC: 4.5-11.0 x10\\u00b3/μL"
                }
                """;

        mockMvc.perform(post("/tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("CBC"))
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    void addTest_withMissingName_returns400() throws Exception {
        String requestBody = """
                {
                  "code": "CBC",
                  "unitPrice": 750.00
                }
                """;

        mockMvc.perform(post("/tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void addTest_withMissingCode_returns400() throws Exception {
        String requestBody = """
                {
                  "name": "Complete Blood Count",
                  "unitPrice": 750.00
                }
                """;

        mockMvc.perform(post("/tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void addTest_withDuplicateCode_returns409() throws Exception {
        when(labTestService.addTest(any()))
                .thenThrow(new BusinessException("DUPLICATE_TEST_CODE", "Test code already exists: CBC", 409));

        String requestBody = """
                {
                  "name": "Complete Blood Count",
                  "code": "CBC",
                  "unitPrice": 750.00
                }
                """;

        mockMvc.perform(post("/tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_TEST_CODE"));
    }

    // ─── PUT /tests/:id ──────────────────────────────────────────────────────────

    @Test
    void updateTest_withValidRequest_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        LabTestDto updated = buildSampleTest();
        updated.setUnitPrice(new BigDecimal("800.00"));
        when(labTestService.updateTest(eq(id), any(LabTestUpdateRequest.class))).thenReturn(updated);

        String requestBody = """
                {
                  "unitPrice": 800.00
                }
                """;

        mockMvc.perform(put("/tests/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unitPrice").value(800.00));
    }

    @Test
    void updateTest_whenNotFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(labTestService.updateTest(eq(id), any()))
                .thenThrow(new ResourceNotFoundException("Test not found: " + id));

        mockMvc.perform(put("/tests/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // ─── DELETE /tests/:id ───────────────────────────────────────────────────────

    @Test
    void deleteTest_whenNoOrders_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(labTestService).deleteTest(id);

        mockMvc.perform(delete("/tests/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteTest_whenOrdersExist_returns422() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new BusinessException("TEST_HAS_ORDERS", "Cannot delete test — orders exist", 422))
                .when(labTestService).deleteTest(id);

        mockMvc.perform(delete("/tests/{id}", id))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TEST_HAS_ORDERS"));
    }
}
