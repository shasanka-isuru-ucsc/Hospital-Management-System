package com.hms.ward.controller;

import com.hms.ward.dto.ApiResponse;
import com.hms.ward.dto.BedDto;
import com.hms.ward.dto.BedStatusUpdateRequest;
import com.hms.ward.service.BedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class BedController {

    private final BedService bedService;

    // ─── GET /beds ────────────────────────────────────────────────────────────────

    @GetMapping("/beds")
    public ResponseEntity<ApiResponse<List<BedDto>>> listBeds(
            @RequestParam(required = false) UUID ward_id,
            @RequestParam(required = false) String status) {

        List<BedDto> beds = bedService.listBeds(ward_id, status);
        return ResponseEntity.ok(ApiResponse.success(beds));
    }

    // ─── PUT /beds/:id ────────────────────────────────────────────────────────────

    @PutMapping("/beds/{id}")
    public ResponseEntity<ApiResponse<BedDto>> updateBedStatus(
            @PathVariable UUID id,
            @Valid @RequestBody BedStatusUpdateRequest request) {

        BedDto bed = bedService.updateBedStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(bed));
    }
}
