package com.hms.lab.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.lab.dto.*;
import com.hms.lab.exception.BusinessException;
import com.hms.lab.exception.ResourceNotFoundException;
import com.hms.lab.service.LabOrderService;
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

@WebMvcTest(LabOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class LabOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LabOrderService labOrderService;

    private LabOrderDto buildSampleOrder() {
        OrderTestDto test = OrderTestDto.builder()
                .id(UUID.randomUUID())
                .testName("Complete Blood Count")
                .testCode("CBC")
                .urgency("routine")
                .unitPrice(new BigDecimal("750.00"))
                .status("pending")
                .build();

        return LabOrderDto.builder()
                .id(UUID.randomUUID())
                .patientName("Andrea Lalema")
                .patientMobile("+94771234567")
                .source("walk_in")
                .tests(List.of(test))
                .totalAmount(new BigDecimal("750.00"))
                .status("registered")
                .paymentStatus("pending")
                .createdBy("lab_tech")
                .createdAt(ZonedDateTime.now())
                .build();
    }

    // ─── POST /orders ────────────────────────────────────────────────────────────

    @Test
    void createOrder_withValidWalkInRequest_returns201() throws Exception {
        LabOrderDto created = buildSampleOrder();
        when(labOrderService.createOrder(any(LabOrderCreateRequest.class), any())).thenReturn(created);

        String requestBody = """
                {
                  "patientName": "Andrea Lalema",
                  "patientMobile": "+94771234567",
                  "source": "walk_in",
                  "tests": [
                    {
                      "testId": "%s",
                      "urgency": "routine"
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("registered"))
                .andExpect(jsonPath("$.data.paymentStatus").value("pending"))
                .andExpect(jsonPath("$.data.source").value("walk_in"));
    }

    @Test
    void createOrder_withEmptyTests_returns400() throws Exception {
        String requestBody = """
                {
                  "patientName": "Andrea Lalema",
                  "tests": []
                }
                """;

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createOrder_withInvalidTestId_returns400() throws Exception {
        when(labOrderService.createOrder(any(), any()))
                .thenThrow(new BusinessException("INVALID_TEST_ID", "Test not found: ...", 400));

        String requestBody = """
                {
                  "patientName": "Andrea Lalema",
                  "tests": [
                    {
                      "testId": "%s"
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TEST_ID"));
    }

    // ─── GET /orders ─────────────────────────────────────────────────────────────

    @Test
    void listOrders_returnsPagedResult() throws Exception {
        LabOrderDto order = buildSampleOrder();
        when(labOrderService.listOrders(any(), any(), any(), any(), any(), eq(1), eq(20)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void listOrders_withStatusFilter_returns200() throws Exception {
        when(labOrderService.listOrders(any(), eq("registered"), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(buildSampleOrder()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/orders").param("status", "registered"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("registered"));
    }

    // ─── GET /orders/:id ─────────────────────────────────────────────────────────

    @Test
    void getOrder_whenFound_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        LabOrderDto order = buildSampleOrder();
        when(labOrderService.getOrderById(id)).thenReturn(order);

        mockMvc.perform(get("/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.patientName").value("Andrea Lalema"));
    }

    @Test
    void getOrder_whenNotFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(labOrderService.getOrderById(id))
                .thenThrow(new ResourceNotFoundException("Order not found: " + id));

        mockMvc.perform(get("/orders/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // ─── PUT /orders/:id/results ─────────────────────────────────────────────────

    @Test
    void enterResults_withValidRequest_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        LabOrderDto order = buildSampleOrder();
        order.setStatus("processing");
        when(labOrderService.enterResults(eq(id), any(ResultsUpdateRequest.class))).thenReturn(order);

        String requestBody = """
                {
                  "results": [
                    {
                      "orderTestId": "%s",
                      "resultValue": "WBC: 9.2 x10\\u00b3/\\u03bcL",
                      "isAbnormal": false,
                      "technicianNotes": "Normal range"
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(put("/orders/{id}/results", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("processing"));
    }

    @Test
    void enterResults_onCancelledOrder_returns422() throws Exception {
        UUID id = UUID.randomUUID();
        when(labOrderService.enterResults(eq(id), any()))
                .thenThrow(new BusinessException("ORDER_CANCELLED", "Cannot enter results for a cancelled order", 422));

        String requestBody = """
                {
                  "results": [
                    {
                      "orderTestId": "%s",
                      "resultValue": "9.2"
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(put("/orders/{id}/results", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ORDER_CANCELLED"));
    }

    // ─── PUT /orders/:id/pay ─────────────────────────────────────────────────────

    @Test
    void recordPayment_withValidRequest_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        LabOrderDto order = buildSampleOrder();
        order.setPaymentStatus("paid");
        when(labOrderService.recordPayment(eq(id), any(PaymentRequest.class))).thenReturn(order);

        String requestBody = """
                {
                  "paymentMethod": "cash",
                  "amountPaid": 750.00
                }
                """;

        mockMvc.perform(put("/orders/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentStatus").value("paid"))
                .andExpect(jsonPath("$.message").value("Payment recorded. Finance service notified."));
    }

    @Test
    void recordPayment_onAlreadyPaidOrder_returns422() throws Exception {
        UUID id = UUID.randomUUID();
        when(labOrderService.recordPayment(eq(id), any()))
                .thenThrow(new BusinessException("ALREADY_PAID", "Order is already paid", 422));

        String requestBody = """
                {
                  "paymentMethod": "cash",
                  "amountPaid": 750.00
                }
                """;

        mockMvc.perform(put("/orders/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ALREADY_PAID"));
    }
}
