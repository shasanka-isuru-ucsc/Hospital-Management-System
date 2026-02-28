package com.hms.finance.controller;

import com.hms.finance.dto.*;
import com.hms.finance.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @Value("${hospital.name:Narammala Channeling Hospital}")
    private String hospitalName;

    // ─── GET /invoices ───────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<InvoiceDto>>> listInvoices(
            @RequestParam(required = false) String billing_module,
            @RequestParam(required = false) String payment_status,
            @RequestParam(required = false) UUID patient_id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        Page<InvoiceDto> result = invoiceService.listInvoices(
                billing_module, payment_status, patient_id, from, to, page, limit);

        // Calculate total_pending_amount for meta
        Double totalPending = result.getContent().stream()
                .filter(inv -> "pending".equals(inv.getPaymentStatus()))
                .map(InvoiceDto::getTotalAmount)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();

        PaginationMeta meta = new PaginationMeta(page, limit, result.getTotalElements(),
                result.getTotalPages(), totalPending);

        return ResponseEntity.ok(ApiResponse.success(result.getContent(), meta));
    }

    // ─── POST /invoices ──────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<InvoiceDto>> createInvoice(
            @Valid @RequestBody InvoiceCreateRequest request,
            @RequestHeader(value = "X-User-Name", defaultValue = "receptionist") String createdBy) {

        InvoiceDto created = invoiceService.createManualInvoice(request, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    // ─── GET /invoices/:id ───────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceDto>> getInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.getInvoiceById(id)));
    }

    // ─── PUT /invoices/:id/pay ───────────────────────────────────────────────────

    @PutMapping("/{id}/pay")
    public ResponseEntity<java.util.Map<String, Object>> recordPayment(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentRequest request) {

        PaymentResultDto result = invoiceService.recordPayment(id, request);

        java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("success", true);
        response.put("data", result.getInvoice());
        response.put("change_amount", result.getChangeAmount());

        return ResponseEntity.ok(response);
    }

    // ─── POST /invoices/:id/discount ─────────────────────────────────────────────

    @PostMapping("/{id}/discount")
    public ResponseEntity<ApiResponse<InvoiceDto>> applyDiscount(
            @PathVariable UUID id,
            @Valid @RequestBody DiscountRequest request) {

        return ResponseEntity.ok(ApiResponse.success(invoiceService.applyDiscount(id, request)));
    }

    // ─── GET /invoices/:id/print ─────────────────────────────────────────────────

    @GetMapping("/{id}/print")
    public ResponseEntity<ApiResponse<PrintDataDto>> getPrintData(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.getPrintData(id, hospitalName)));
    }
}
