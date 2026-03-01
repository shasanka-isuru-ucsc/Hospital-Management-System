package com.hms.lab.controller;

import com.hms.lab.dto.*;
import com.hms.lab.service.LabOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class LabOrderController {

    private final LabOrderService labOrderService;

    // ─── POST /orders ────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<LabOrderDto>> createOrder(
            @Valid @RequestBody LabOrderCreateRequest request,
            @RequestHeader(value = "X-User-Name", defaultValue = "lab_tech") String createdBy) {

        LabOrderDto created = labOrderService.createOrder(request, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    // ─── GET /orders ─────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<LabOrderDto>>> listOrders(
            @RequestParam(required = false) UUID patient_id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String payment_status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        Page<LabOrderDto> result = labOrderService.listOrders(
                patient_id, status, payment_status, source, date, page, limit);

        PaginationMeta meta = new PaginationMeta(
                page, limit, result.getTotalElements(), result.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(result.getContent(), meta));
    }

    // ─── GET /orders/:id ─────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LabOrderDto>> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(labOrderService.getOrderById(id)));
    }

    // ─── PUT /orders/:id/results ─────────────────────────────────────────────────

    @PutMapping("/{id}/results")
    public ResponseEntity<ApiResponse<LabOrderDto>> enterResults(
            @PathVariable UUID id,
            @Valid @RequestBody ResultsUpdateRequest request) {

        return ResponseEntity.ok(ApiResponse.success(labOrderService.enterResults(id, request)));
    }

    // ─── POST /orders/:id/report ─────────────────────────────────────────────────

    @PostMapping(value = "/{id}/report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ReportUploadResponse>> uploadReport(
            @PathVariable UUID id,
            @RequestPart("report") MultipartFile report,
            @RequestPart(value = "notes", required = false) String notes) {

        ReportUploadResponse response = labOrderService.uploadReport(id, report, notes);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ─── PUT /orders/:id/pay ─────────────────────────────────────────────────────

    @PutMapping("/{id}/pay")
    public ResponseEntity<Map<String, Object>> recordPayment(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentRequest request) {

        LabOrderDto order = labOrderService.recordPayment(id, request);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", order);
        response.put("message", "Payment recorded. Finance service notified.");

        return ResponseEntity.ok(response);
    }
}
