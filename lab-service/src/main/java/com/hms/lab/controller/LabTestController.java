package com.hms.lab.controller;

import com.hms.lab.dto.ApiResponse;
import com.hms.lab.dto.LabTestCreateRequest;
import com.hms.lab.dto.LabTestDto;
import com.hms.lab.dto.LabTestUpdateRequest;
import com.hms.lab.service.LabTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/tests")
@RequiredArgsConstructor
public class LabTestController {

    private final LabTestService labTestService;

    // ─── GET /tests ──────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<LabTestDto>>> getTestCatalog(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean is_active) {

        List<LabTestDto> tests = labTestService.getTestCatalog(category, search, is_active);
        return ResponseEntity.ok(ApiResponse.success(tests));
    }

    // ─── POST /tests ─────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<LabTestDto>> addTest(
            @Valid @RequestBody LabTestCreateRequest request) {

        LabTestDto created = labTestService.addTest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    // ─── PUT /tests/:id ──────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LabTestDto>> updateTest(
            @PathVariable UUID id,
            @Valid @RequestBody LabTestUpdateRequest request) {

        return ResponseEntity.ok(ApiResponse.success(labTestService.updateTest(id, request)));
    }

    // ─── DELETE /tests/:id ───────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTest(@PathVariable UUID id) {
        labTestService.deleteTest(id);
        return ResponseEntity.noContent().build();
    }
}
