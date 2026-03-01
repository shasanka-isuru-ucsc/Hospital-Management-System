package com.hms.staff.controller;

import com.hms.staff.dto.ApiResponse;
import com.hms.staff.dto.NurseAllocationCreateRequest;
import com.hms.staff.dto.NurseAllocationDto;
import com.hms.staff.service.NurseAllocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/nurse-allocations")
@RequiredArgsConstructor
public class NurseAllocationController {

    private final NurseAllocationService nurseAllocationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NurseAllocationDto>>> listAllocations(
            @RequestParam(required = false) UUID doctor_id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(
                nurseAllocationService.listAllocations(doctor_id, date, status)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NurseAllocationDto>> allocateNurse(
            @Valid @RequestBody NurseAllocationCreateRequest request,
            @RequestHeader(value = "X-User-Name", defaultValue = "admin") String allocatedBy) {
        NurseAllocationDto dto = nurseAllocationService.allocateNurse(request, allocatedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(dto));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<NurseAllocationDto>> completeAllocation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(nurseAllocationService.completeAllocation(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<NurseAllocationDto>> cancelAllocation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(nurseAllocationService.cancelAllocation(id)));
    }
}
