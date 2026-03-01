package com.hms.staff.controller;

import com.hms.staff.dto.ApiResponse;
import com.hms.staff.dto.StaffMemberCreateRequest;
import com.hms.staff.dto.StaffMemberDto;
import com.hms.staff.service.StaffMemberService;
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
@RequiredArgsConstructor
public class StaffController {

    private final StaffMemberService staffMemberService;

    /**
     * GET /staff/available?date=&role=nurse
     * Returns nurses (or other staff by role) not already allocated on the given date.
     */
    @GetMapping("/staff/available")
    public ResponseEntity<ApiResponse<List<StaffMemberDto>>> getAvailableStaff(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "nurse") String role) {
        return ResponseEntity.ok(ApiResponse.success(staffMemberService.getAvailableStaff(date, role)));
    }

    /**
     * POST /staff — Admin endpoint to register non-doctor staff (nurses, receptionists).
     */
    @PostMapping("/staff")
    public ResponseEntity<ApiResponse<StaffMemberDto>> createStaffMember(
            @Valid @RequestBody StaffMemberCreateRequest request) {
        StaffMemberDto dto = staffMemberService.createStaffMember(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(dto));
    }

    /**
     * GET /staff — List all staff members.
     */
    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<List<StaffMemberDto>>> listStaff() {
        return ResponseEntity.ok(ApiResponse.success(staffMemberService.listAllStaff()));
    }

    /**
     * GET /staff/{id} — Get a single staff member.
     */
    @GetMapping("/staff/{id}")
    public ResponseEntity<ApiResponse<StaffMemberDto>> getStaffMember(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(staffMemberService.getById(id)));
    }
}
