package com.hms.clinical.controller;

import com.hms.clinical.dto.ApiResponse;
import com.hms.clinical.service.PatientHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/patients/{patientId}")
@RequiredArgsConstructor
public class PatientHistoryController {

    private final PatientHistoryService patientHistoryService;

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Object>> getPatientHistory(
            @PathVariable UUID patientId,
            @RequestParam(required = false) String sessionType,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        Map<String, Object> result = patientHistoryService.getPatientHistory(
                patientId, sessionType, from, to, page, limit);

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .data(result.get("data"))
                .meta(result.get("meta"))
                .build());
    }
}
