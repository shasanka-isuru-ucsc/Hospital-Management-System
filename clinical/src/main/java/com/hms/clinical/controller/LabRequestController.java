package com.hms.clinical.controller;

import com.hms.clinical.dto.*;
import com.hms.clinical.service.LabRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sessions/{id}/lab-requests")
@RequiredArgsConstructor
public class LabRequestController {

    private final LabRequestService labRequestService;

    @PostMapping
    public ResponseEntity<ApiResponse<List<LabRequestDto>>> createLabRequests(
            @PathVariable UUID id,
            @Valid @RequestBody LabRequestCreateRequest request) {

        List<LabRequestDto> labRequests = labRequestService.createLabRequests(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<List<LabRequestDto>>builder()
                        .success(true)
                        .data(labRequests)
                        .message("Lab request sent. Lab Service will create the order.")
                        .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LabRequestDto>>> getLabRequests(@PathVariable UUID id) {
        List<LabRequestDto> labRequests = labRequestService.getLabRequests(id);
        return ResponseEntity.ok(ApiResponse.<List<LabRequestDto>>builder().success(true).data(labRequests).build());
    }
}
