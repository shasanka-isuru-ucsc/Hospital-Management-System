package com.hms.finance.controller;

import com.hms.finance.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    // ─── GET /reports/summary ─────────────────────────────────────────────────────

    @Test
    void getSummary_withDefaultPeriod_returns200() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total_appointments", 250);
        data.put("appointments_change_pct", 40.0);
        data.put("new_patients", 140);
        data.put("patients_change_pct", 20.0);
        data.put("total_operations", 56);
        data.put("operations_change_pct", -15.0);
        data.put("total_earnings", new BigDecimal("20250.00"));
        data.put("earnings_change_pct", 30.0);
        data.put("period", "this_month");

        when(reportService.getSummary("this_month")).thenReturn(data);

        mockMvc.perform(get("/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.period").value("this_month"))
                .andExpect(jsonPath("$.data.total_appointments").value(250));
    }

    @Test
    void getSummary_withCustomPeriod_returns200() throws Exception {
        Map<String, Object> data = Map.of("period", "today", "total_earnings", BigDecimal.ZERO);
        when(reportService.getSummary("today")).thenReturn(data);

        mockMvc.perform(get("/reports/summary").param("period", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("today"));
    }

    // ─── GET /reports/earnings ────────────────────────────────────────────────────

    @Test
    void getEarnings_withDefaultYear_returns200() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("year", 2026);
        data.put("monthly", List.of(Map.of("month", 1, "month_name", "Jan", "total", BigDecimal.ZERO)));

        when(reportService.getEarningsByYear(anyInt())).thenReturn(data);

        mockMvc.perform(get("/reports/earnings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.year").value(2026));
    }

    @Test
    void getEarnings_withSpecificYear_returns200() throws Exception {
        Map<String, Object> data = Map.of("year", 2025, "monthly", List.of());
        when(reportService.getEarningsByYear(2025)).thenReturn(data);

        mockMvc.perform(get("/reports/earnings").param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(2025));
    }

    // ─── GET /reports/departments ─────────────────────────────────────────────────

    @Test
    void getDepartments_withDefaultPeriod_returns200() throws Exception {
        List<Map<String, Object>> data = List.of(
                Map.of("department_name", "General OPD", "patient_count", 87, "revenue", new BigDecimal("12500.00"))
        );
        when(reportService.getDepartmentStats("this_month")).thenReturn(data);

        mockMvc.perform(get("/reports/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].department_name").value("General OPD"));
    }
}
