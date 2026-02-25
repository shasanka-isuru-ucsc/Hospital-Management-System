package com.hms.staff.controller;

import com.hms.staff.dto.ApiResponse;
import com.hms.staff.dto.DepartmentDto;
import com.hms.staff.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentDto>>> getAllDepartments() {
        return ResponseEntity.ok(ApiResponse.success(departmentService.getAllDepartments()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentDto>> createDepartment(@RequestBody DepartmentDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(departmentService.createDepartment(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentDto>> updateDepartment(@PathVariable UUID id,
            @RequestBody DepartmentDto dto) {
        return ResponseEntity.ok(ApiResponse.success(departmentService.updateDepartment(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable UUID id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }
}
