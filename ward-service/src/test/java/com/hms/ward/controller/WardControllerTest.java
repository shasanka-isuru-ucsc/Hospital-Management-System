package com.hms.ward.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.ward.dto.WardDto;
import com.hms.ward.service.WardManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WardController.class)
@AutoConfigureMockMvc(addFilters = false)
class WardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WardManagementService wardManagementService;

    private WardDto buildSampleWard() {
        return WardDto.builder()
                .id(UUID.randomUUID())
                .name("Male General Ward")
                .type("general")
                .capacity(20)
                .occupied(14)
                .available(6)
                .maintenance(0)
                .isActive(true)
                .build();
    }

    // ─── GET /wards ───────────────────────────────────────────────────────────────

    @Test
    void listWards_returnsWardList() throws Exception {
        when(wardManagementService.listWards(any(), any()))
                .thenReturn(List.of(buildSampleWard()));

        mockMvc.perform(get("/wards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("Male General Ward"))
                .andExpect(jsonPath("$.data[0].occupied").value(14))
                .andExpect(jsonPath("$.data[0].available").value(6));
    }

    @Test
    void listWards_withTypeFilter_returnsFilteredList() throws Exception {
        when(wardManagementService.listWards("icu", null)).thenReturn(List.of());

        mockMvc.perform(get("/wards").param("type", "icu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ─── POST /wards ──────────────────────────────────────────────────────────────

    @Test
    void createWard_withValidRequest_returns201() throws Exception {
        WardDto created = buildSampleWard();
        when(wardManagementService.createWard(any())).thenReturn(created);

        String requestBody = """
                {
                  "name": "Male General Ward",
                  "type": "general",
                  "capacity": 20
                }
                """;

        mockMvc.perform(post("/wards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Male General Ward"))
                .andExpect(jsonPath("$.message").value("Ward created with 20 beds (M-101 to M-120)"));
    }

    @Test
    void createWard_withMissingName_returns400() throws Exception {
        String requestBody = """
                {
                  "type": "general",
                  "capacity": 10
                }
                """;

        mockMvc.perform(post("/wards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createWard_withZeroCapacity_returns400() throws Exception {
        String requestBody = """
                {
                  "name": "Test Ward",
                  "type": "general",
                  "capacity": 0
                }
                """;

        mockMvc.perform(post("/wards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
