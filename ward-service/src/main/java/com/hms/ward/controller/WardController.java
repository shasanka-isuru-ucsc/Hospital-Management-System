package com.hms.ward.controller;

import com.hms.ward.dto.ApiResponse;
import com.hms.ward.dto.WardCreateRequest;
import com.hms.ward.dto.WardDto;
import com.hms.ward.service.WardManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/wards")
@RequiredArgsConstructor
public class WardController {

    private final WardManagementService wardManagementService;

    // ─── GET /wards ───────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<WardDto>>> listWards(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean is_active) {

        List<WardDto> wards = wardManagementService.listWards(type, is_active);
        return ResponseEntity.ok(ApiResponse.success(wards));
    }

    // ─── POST /wards ──────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Map<String, Object>> createWard(
            @Valid @RequestBody WardCreateRequest request) {

        WardDto ward = wardManagementService.createWard(request);

        String prefix = request.getName() != null && !request.getName().isBlank()
                ? String.valueOf(Character.toUpperCase(request.getName().charAt(0)))
                : "B";
        String firstBed = prefix + "-101";
        String lastBed = prefix + "-" + String.format("%03d", 100 + request.getCapacity());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", ward);
        response.put("message", String.format("Ward created with %d beds (%s to %s)",
                request.getCapacity(), firstBed, lastBed));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
