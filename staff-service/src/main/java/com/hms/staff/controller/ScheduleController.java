package com.hms.staff.controller;

import com.hms.staff.dto.ApiResponse;
import com.hms.staff.dto.DoctorAvailabilityDto;
import com.hms.staff.dto.ScheduleCreateRequest;
import com.hms.staff.dto.ScheduleDto;
import com.hms.staff.dto.ScheduleUpdateRequest;
import com.hms.staff.service.ScheduleService;
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
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/doctors/{id}/schedule")
    public ResponseEntity<ApiResponse<List<ScheduleDto>>> getDoctorSchedule(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getDoctorSchedule(id)));
    }

    @PostMapping("/doctors/{id}/schedule")
    public ResponseEntity<ApiResponse<ScheduleDto>> addScheduleEntry(
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleCreateRequest request) {
        ScheduleDto dto = scheduleService.addScheduleEntry(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(dto));
    }

    @GetMapping("/doctors/{id}/availability")
    public ResponseEntity<ApiResponse<DoctorAvailabilityDto>> getDoctorAvailability(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getDoctorAvailability(id, date)));
    }

    @PutMapping("/schedules/{id}")
    public ResponseEntity<ApiResponse<ScheduleDto>> updateScheduleEntry(
            @PathVariable UUID id,
            @RequestBody ScheduleUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.updateScheduleEntry(id, request)));
    }

    @DeleteMapping("/schedules/{id}")
    public ResponseEntity<Void> deleteScheduleEntry(@PathVariable UUID id) {
        scheduleService.deleteScheduleEntry(id);
        return ResponseEntity.noContent().build();
    }
}
