package com.hms.ward.controller;

import com.hms.ward.dto.*;
import com.hms.ward.service.AdmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AdmissionController {

    private final AdmissionService admissionService;

    // ─── POST /admissions ─────────────────────────────────────────────────────────

    @PostMapping("/admissions")
    public ResponseEntity<ApiResponse<AdmissionDto>> admitPatient(
            @Valid @RequestBody AdmissionCreateRequest request) {

        AdmissionDto admission = admissionService.admitPatient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(admission));
    }

    // ─── GET /admissions ──────────────────────────────────────────────────────────

    @GetMapping("/admissions")
    public ResponseEntity<ApiResponse<java.util.List<AdmissionDto>>> listAdmissions(
            @RequestParam(required = false) UUID ward_id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID attending_doctor_id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        Page<AdmissionDto> result = admissionService.listAdmissions(
                ward_id, status, attending_doctor_id, from, to, page, limit);

        PaginationMeta meta = new PaginationMeta(
                page, limit, result.getTotalElements(), result.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(result.getContent(), meta));
    }

    // ─── GET /admissions/:id ──────────────────────────────────────────────────────

    @GetMapping("/admissions/{id}")
    public ResponseEntity<ApiResponse<AdmissionDto>> getAdmission(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(admissionService.getAdmissionById(id)));
    }

    // ─── PUT /admissions/:id/discharge ────────────────────────────────────────────

    @PutMapping("/admissions/{id}/discharge")
    public ResponseEntity<Map<String, Object>> dischargePatient(
            @PathVariable UUID id,
            @RequestBody(required = false) DischargeRequest request) {

        AdmissionDto admission = admissionService.dischargePatient(id, request);

        BigDecimal finalTotal = admission.getRunningTotal();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", admission);
        response.put("message", "Patient discharged. Ward invoice will be created automatically.");
        response.put("final_total", finalTotal);

        return ResponseEntity.ok(response);
    }

    // ─── GET /patients/:patientId/admissions ──────────────────────────────────────

    @GetMapping("/patients/{patientId}/admissions")
    public ResponseEntity<ApiResponse<java.util.List<AdmissionDto>>> getPatientAdmissionHistory(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {

        Page<AdmissionDto> result = admissionService.getPatientAdmissionHistory(patientId, page, limit);

        PaginationMeta meta = new PaginationMeta(
                page, limit, result.getTotalElements(), result.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(result.getContent(), meta));
    }
}
