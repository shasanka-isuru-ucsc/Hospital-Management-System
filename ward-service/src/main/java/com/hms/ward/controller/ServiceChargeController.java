package com.hms.ward.controller;

import com.hms.ward.dto.ApiResponse;
import com.hms.ward.dto.ServiceListResponseData;
import com.hms.ward.dto.WardServiceCreateRequest;
import com.hms.ward.dto.WardServiceDto;
import com.hms.ward.service.ServiceChargeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admissions/{id}/services")
@RequiredArgsConstructor
public class ServiceChargeController {

    private final ServiceChargeService serviceChargeService;

    // ─── POST /admissions/:id/services ────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Map<String, Object>> addServiceCharge(
            @PathVariable UUID id,
            @Valid @RequestBody WardServiceCreateRequest request,
            @RequestHeader(value = "X-User-Name", defaultValue = "ward_staff") String addedBy) {

        WardServiceDto serviceDto = serviceChargeService.addServiceCharge(id, request, addedBy);

        // Compute running total by fetching the service list
        ServiceListResponseData listData = serviceChargeService.getServicesForAdmission(id, null);
        BigDecimal runningTotal = listData.getRunningTotal();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", serviceDto);
        response.put("running_total", runningTotal);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─── GET /admissions/:id/services ─────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<ServiceListResponseData>> getServicesForAdmission(
            @PathVariable UUID id,
            @RequestParam(required = false) String service_type) {

        ServiceListResponseData data = serviceChargeService.getServicesForAdmission(id, service_type);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ─── DELETE /admissions/:id/services/:serviceId ───────────────────────────────

    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Map<String, Object>> removeServiceCharge(
            @PathVariable UUID id,
            @PathVariable UUID serviceId) {

        BigDecimal runningTotal = serviceChargeService.removeServiceCharge(id, serviceId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", String.format("Service removed. Updated running total: LKR %.2f", runningTotal));
        response.put("running_total", runningTotal);

        return ResponseEntity.ok(response);
    }
}
