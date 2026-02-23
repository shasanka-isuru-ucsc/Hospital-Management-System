package com.hms.clinical.controller;

import com.hms.clinical.dto.*;
import com.hms.clinical.service.PrescriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pharmacy")
@RequiredArgsConstructor
public class PharmacyController {

    private final PrescriptionService prescriptionService;

    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<List<PrescriptionDto>>> getPharmacyQueue(
            @RequestParam(defaultValue = "pending") String status) {

        List<PrescriptionDto> queue = prescriptionService.getPharmacyQueue(status);
        return ResponseEntity.ok(ApiResponse.<List<PrescriptionDto>>builder().success(true).data(queue).build());
    }

    @PutMapping("/{rxId}/dispense")
    public ResponseEntity<ApiResponse<PrescriptionDto>> dispensePrescription(@PathVariable UUID rxId) {
        PrescriptionDto rx = prescriptionService.dispensePrescription(rxId);
        return ResponseEntity.ok(ApiResponse.<PrescriptionDto>builder().success(true).data(rx).build());
    }
}
