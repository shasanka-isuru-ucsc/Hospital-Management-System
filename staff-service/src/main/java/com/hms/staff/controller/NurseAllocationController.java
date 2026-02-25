package com.hms.staff.controller;

import com.hms.staff.dto.ApiResponse;
import com.hms.staff.dto.NurseAllocationDto;
import com.hms.staff.dto.StaffAvailableDto;
import com.hms.staff.service.NurseAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class NurseAllocationController {

    private final NurseAllocationService nurseAllocationService;

    @GetMapping("/nurse-allocations")
    public ResponseEntity<ApiResponse<List<NurseAllocationDto>>> getAllocations(
            @RequestParam(required = false) UUID doctor_id,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(nurseAllocationService.getAllocations(doctor_id, date, status)));
    }

    @PostMapping("/nurse-allocations")
    public ResponseEntity<ApiResponse<NurseAllocationDto>> allocateNurse(@RequestBody NurseAllocationDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(nurseAllocationService.allocateNurse(dto)));
    }

    @PutMapping("/nurse-allocations/{id}/complete")
    public ResponseEntity<ApiResponse<NurseAllocationDto>> completeAllocation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(nurseAllocationService.completeAllocation(id)));
    }

    @DeleteMapping("/nurse-allocations/{id}")
    public ResponseEntity<ApiResponse<NurseAllocationDto>> cancelAllocation(@PathVariable UUID id) {
        nurseAllocationService.cancelAllocation(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Allocation cancelled"));
    }

    @GetMapping("/staff/available")
    public ResponseEntity<ApiResponse<List<StaffAvailableDto>>> getAvailableStaff(
            @RequestParam LocalDate date,
            @RequestParam(defaultValue = "nurse") String role) {
        return ResponseEntity.ok(ApiResponse.success(nurseAllocationService.getAvailableStaff(date, role)));
    }
}
