package com.hms.finance.controller;

import com.hms.finance.dto.ApiResponse;
import com.hms.finance.dto.PrescriptionPrintRequest;
import com.hms.finance.service.PrescriptionPdfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionPdfService prescriptionPdfService;

    @Value("${hospital.name:Narammala Channeling Hospital}")
    private String defaultHospitalName;

    // ─── POST /prescriptions/print ────────────────────────────────────────────────

    @PostMapping("/print")
    public ResponseEntity<ApiResponse<Map<String, Object>>> printPrescription(
            @Valid @RequestBody PrescriptionPrintRequest request) {

        String hospitalName = request.getHospitalName() != null
                ? request.getHospitalName()
                : defaultHospitalName;

        Map<String, Object> result = prescriptionPdfService.generateAndUpload(
                request.getSessionId(),
                request.getDoctorName(),
                hospitalName
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
