package com.hms.clinical.controller;

import com.hms.clinical.dto.*;
import com.hms.clinical.service.PrescriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @PostMapping("/sessions/{id}/prescriptions")
    public ResponseEntity<ApiResponse<List<PrescriptionDto>>> addPrescriptions(
            @PathVariable UUID id,
            @Valid @RequestBody PrescriptionCreateRequest request) {

        List<PrescriptionDto> prescriptions = prescriptionService.addPrescriptions(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<List<PrescriptionDto>>builder().success(true).data(prescriptions).build());
    }

    @GetMapping("/sessions/{id}/prescriptions")
    public ResponseEntity<ApiResponse<List<PrescriptionDto>>> getPrescriptions(
            @PathVariable UUID id,
            @RequestParam(required = false) String type) {

        List<PrescriptionDto> prescriptions = prescriptionService.getPrescriptions(id, type);
        return ResponseEntity
                .ok(ApiResponse.<List<PrescriptionDto>>builder().success(true).data(prescriptions).build());
    }

    @DeleteMapping("/sessions/{id}/prescriptions/{rxId}")
    public ResponseEntity<Void> deletePrescription(@PathVariable UUID id, @PathVariable UUID rxId) {
        prescriptionService.deletePrescription(id, rxId);
        return ResponseEntity.noContent().build();
    }
}
