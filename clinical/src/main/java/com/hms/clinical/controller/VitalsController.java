package com.hms.clinical.controller;

import com.hms.clinical.dto.*;
import com.hms.clinical.service.VitalsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class VitalsController {

    private final VitalsService vitalsService;

    @PostMapping("/sessions/{id}/vitals")
    public ResponseEntity<ApiResponse<VitalsDto>> recordVitals(
            @PathVariable UUID id,
            @Valid @RequestBody VitalsCreateRequest request) {

        VitalsDto vitals = vitalsService.recordVitals(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<VitalsDto>builder().success(true).data(vitals).build());
    }

    @GetMapping("/sessions/{id}/vitals")
    public ResponseEntity<ApiResponse<VitalsDto>> getVitals(@PathVariable UUID id) {
        VitalsDto vitals = vitalsService.getVitals(id);
        return ResponseEntity.ok(ApiResponse.<VitalsDto>builder().success(true).data(vitals).build());
    }

    @GetMapping("/patients/{patientId}/vitals-history")
    public ResponseEntity<ApiResponse<List<VitalsDto>>> getVitalsHistory(
            @PathVariable UUID patientId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {

        List<VitalsDto> history = vitalsService.getVitalsHistory(patientId, from, to);
        return ResponseEntity.ok(ApiResponse.<List<VitalsDto>>builder().success(true).data(history).build());
    }
}
