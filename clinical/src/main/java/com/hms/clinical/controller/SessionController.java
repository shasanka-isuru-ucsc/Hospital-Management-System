package com.hms.clinical.controller;

import com.hms.clinical.dto.*;
import com.hms.clinical.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    public ResponseEntity<ApiResponse<SessionDto>> createSession(
            @Valid @RequestBody SessionCreateRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "00000000-0000-0000-0000-000000000001") String userId,
            @RequestHeader(value = "X-User-Name", defaultValue = "Dr. System") String userName) {

        SessionDto session = sessionService.createSession(request, UUID.fromString(userId), userName);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<SessionDto>builder().success(true).data(session).build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> listSessions(
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) String sessionType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        Page<SessionDto> sessions = sessionService.listSessions(patientId, doctorId, sessionType, status, date, page,
                limit);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .data(sessions.getContent())
                .meta(Map.of("page", page, "total", sessions.getTotalElements()))
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SessionDto>> getSession(@PathVariable UUID id) {
        SessionDto session = sessionService.getSession(id);
        return ResponseEntity.ok(ApiResponse.<SessionDto>builder().success(true).data(session).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SessionDto>> updateSession(
            @PathVariable UUID id,
            @RequestBody SessionUpdateRequest request) {

        SessionDto session = sessionService.updateSession(id, request);
        return ResponseEntity.ok(ApiResponse.<SessionDto>builder().success(true).data(session).build());
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<SessionDto>> completeSession(
            @PathVariable UUID id,
            @Valid @RequestBody SessionCompleteRequest request) {

        SessionDto session = sessionService.completeSession(id, request);
        return ResponseEntity.ok(ApiResponse.<SessionDto>builder()
                .success(true)
                .data(session)
                .message("Session completed. Invoice will be created automatically.")
                .build());
    }
}
