package com.hms.finance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.finance.dto.*;
import com.hms.finance.exception.BusinessException;
import com.hms.finance.exception.ResourceNotFoundException;
import com.hms.finance.service.InvoiceService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InvoiceController.class)
@AutoConfigureMockMvc(addFilters = false)
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InvoiceService invoiceService;

    private InvoiceDto buildSampleInvoice() {
        LineItemDto lineItem = LineItemDto.builder()
                .id(UUID.randomUUID())
                .itemName("Doctor Consultation")
                .itemType("consultation")
                .quantity(1)
                .unitPrice(new BigDecimal("1500.00"))
                .totalPrice(new BigDecimal("1500.00"))
                .build();

        return InvoiceDto.builder()
                .id(UUID.randomUUID())
                .invoiceNumber("INV-2026-00001")
                .patientId(UUID.randomUUID())
                .patientName("Andrea Lalema")
                .billingModule("opd")
                .lineItems(List.of(lineItem))
                .subtotal(new BigDecimal("1500.00"))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("1500.00"))
                .paymentStatus("pending")
                .source("manual")
                .createdBy("receptionist")
                .createdAt(ZonedDateTime.now())
                .build();
    }

    // ─── POST /invoices ──────────────────────────────────────────────────────────

    @Test
    void createInvoice_withValidRequest_returns201() throws Exception {
        InvoiceDto created = buildSampleInvoice();
        when(invoiceService.createManualInvoice(any(InvoiceCreateRequest.class), any()))
                .thenReturn(created);

        String requestBody = """
                {
                  "patientId": "%s",
                  "patientName": "Andrea Lalema",
                  "billingModule": "opd",
                  "lineItems": [
                    {
                      "itemName": "Doctor Consultation",
                      "itemType": "consultation",
                      "quantity": 1,
                      "unitPrice": 1500.00
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.invoiceNumber").value("INV-2026-00001"))
                .andExpect(jsonPath("$.data.paymentStatus").value("pending"));
    }

    @Test
    void createInvoice_withMissingPatientId_returns400() throws Exception {
        String requestBody = """
                {
                  "billingModule": "opd",
                  "lineItems": [
                    {
                      "itemName": "Doctor Consultation",
                      "itemType": "consultation",
                      "quantity": 1,
                      "unitPrice": 1500.00
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createInvoice_withEmptyLineItems_returns400() throws Exception {
        String requestBody = """
                {
                  "patientId": "%s",
                  "patientName": "Andrea Lalema",
                  "billingModule": "opd",
                  "lineItems": []
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /invoices ───────────────────────────────────────────────────────────

    @Test
    void listInvoices_returnsPagedResult() throws Exception {
        InvoiceDto invoice = buildSampleInvoice();
        when(invoiceService.listInvoices(any(), any(), any(), any(), any(), eq(1), eq(20)))
                .thenReturn(new PageImpl<>(List.of(invoice), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    // ─── GET /invoices/:id ───────────────────────────────────────────────────────

    @Test
    void getInvoiceById_whenFound_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        InvoiceDto invoice = buildSampleInvoice();
        when(invoiceService.getInvoiceById(id)).thenReturn(invoice);

        mockMvc.perform(get("/invoices/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.invoiceNumber").value("INV-2026-00001"));
    }

    @Test
    void getInvoiceById_whenNotFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.getInvoiceById(id))
                .thenThrow(new ResourceNotFoundException("Invoice not found: " + id));

        mockMvc.perform(get("/invoices/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // ─── PUT /invoices/:id/pay ───────────────────────────────────────────────────

    @Test
    void recordPayment_withValidRequest_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        InvoiceDto paidInvoice = buildSampleInvoice();
        paidInvoice.setPaymentStatus("paid");
        paidInvoice.setPaymentMethod("cash");

        when(invoiceService.recordPayment(eq(id), any(PaymentRequest.class)))
                .thenReturn(new PaymentResultDto(paidInvoice, BigDecimal.ZERO));

        String requestBody = """
                {
                  "paymentMethod": "cash",
                  "amountPaid": 1500.00
                }
                """;

        mockMvc.perform(put("/invoices/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentStatus").value("paid"))
                .andExpect(jsonPath("$.change_amount").value(0));
    }

    @Test
    void recordPayment_onAlreadyPaidInvoice_returns422() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.recordPayment(eq(id), any(PaymentRequest.class)))
                .thenThrow(new BusinessException("ALREADY_PAID", "Invoice is already fully paid", 422));

        String requestBody = """
                {
                  "paymentMethod": "cash",
                  "amountPaid": 1500.00
                }
                """;

        mockMvc.perform(put("/invoices/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ALREADY_PAID"));
    }

    // ─── POST /invoices/:id/discount ─────────────────────────────────────────────

    @Test
    void applyDiscount_withValidRequest_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        InvoiceDto discounted = buildSampleInvoice();
        discounted.setDiscountAmount(new BigDecimal("150.00"));
        discounted.setTotalAmount(new BigDecimal("1350.00"));

        when(invoiceService.applyDiscount(eq(id), any(DiscountRequest.class)))
                .thenReturn(discounted);

        String requestBody = """
                {
                  "discountType": "percentage",
                  "discountValue": 10,
                  "reason": "Staff discount"
                }
                """;

        mockMvc.perform(post("/invoices/{id}/discount", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.discountAmount").value(150.00));
    }

    @Test
    void applyDiscount_onPaidInvoice_returns422() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.applyDiscount(eq(id), any(DiscountRequest.class)))
                .thenThrow(new BusinessException("ALREADY_PAID", "Cannot apply discount to a paid invoice", 422));

        String requestBody = """
                {
                  "discountType": "flat",
                  "discountValue": 100,
                  "reason": "Goodwill"
                }
                """;

        mockMvc.perform(post("/invoices/{id}/discount", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ALREADY_PAID"));
    }

    // ─── GET /invoices/:id/print ─────────────────────────────────────────────────

    @Test
    void getPrintData_whenFound_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        PrintDataDto printData = PrintDataDto.builder()
                .invoiceNumber("INV-2026-00001")
                .hospitalName("Narammala Channeling Hospital")
                .patientName("Andrea Lalema")
                .subtotal(new BigDecimal("1500.00"))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("1500.00"))
                .cashierName("receptionist")
                .build();

        when(invoiceService.getPrintData(eq(id), any())).thenReturn(printData);

        mockMvc.perform(get("/invoices/{id}/print", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.invoiceNumber").value("INV-2026-00001"))
                .andExpect(jsonPath("$.data.hospitalName").value("Narammala Channeling Hospital"));
    }
}
