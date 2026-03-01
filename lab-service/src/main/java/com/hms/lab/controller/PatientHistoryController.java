package com.hms.lab.controller;

import com.hms.lab.dto.ApiResponse;
import com.hms.lab.dto.LabOrderDto;
import com.hms.lab.dto.PaginationMeta;
import com.hms.lab.service.LabOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/patients")
@RequiredArgsConstructor
public class PatientHistoryController {

    private final LabOrderService labOrderService;

    // ─── GET /patients/:patientId/history ────────────────────────────────────────

    @GetMapping("/{patientId}/history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPatientHistory(
            @PathVariable UUID patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        Page<LabOrderDto> result = labOrderService.getPatientHistory(patientId, from, to, page, limit);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("patient_id", patientId);
        data.put("orders", result.getContent());

        PaginationMeta meta = new PaginationMeta(
                page, limit, result.getTotalElements(), result.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(data, meta));
    }
}
