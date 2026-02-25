package com.hms.staff.controller;

import com.hms.staff.dto.ApiResponse;
import com.hms.staff.dto.AvailableSlotDto;
import com.hms.staff.dto.ScheduleDto;
import com.hms.staff.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/doctors/{id}/schedule")
    public ResponseEntity<ApiResponse<List<ScheduleDto>>> getDoctorSchedule(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getDoctorSchedule(id)));
    }

    @PostMapping("/doctors/{id}/schedule")
    public ResponseEntity<ApiResponse<ScheduleDto>> addScheduleEntry(@PathVariable UUID id,
            @RequestBody ScheduleDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(scheduleService.addScheduleEntry(id, dto)));
    }

    @PutMapping("/schedules/{id}")
    public ResponseEntity<ApiResponse<ScheduleDto>> updateScheduleEntry(@PathVariable UUID id,
            @RequestBody ScheduleDto dto) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.updateScheduleEntry(id, dto)));
    }

    @DeleteMapping("/schedules/{id}")
    public ResponseEntity<Void> deleteScheduleEntry(@PathVariable UUID id) {
        scheduleService.deleteScheduleEntry(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/doctors/{id}/availability")
    public ResponseEntity<ApiResponse<List<AvailableSlotDto>>> getAvailability(
            @PathVariable UUID id,
            @RequestParam LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getAvailability(id, date)));
    }
}
