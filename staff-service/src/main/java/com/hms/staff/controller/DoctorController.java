package com.hms.staff.controller;

import com.hms.staff.dto.ApiResponse;
import com.hms.staff.dto.DoctorCreateRequest;
import com.hms.staff.dto.DoctorDto;
import com.hms.staff.dto.PaginationMeta;
import com.hms.staff.service.DoctorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DoctorDto>>> getDoctors(
            @RequestParam(required = false) UUID department_id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        Page<DoctorDto> doctors = doctorService.getDoctors(department_id, status, search, page, limit);
        PaginationMeta meta = new PaginationMeta(page, limit, doctors.getTotalElements(), doctors.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(doctors.getContent(), meta));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DoctorDto>> createDoctor(
            @RequestPart("data") @Valid DoctorCreateRequest data,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar,
            @RequestPart(value = "banner", required = false) MultipartFile banner) {

        DoctorDto createdDoctor = doctorService.createDoctor(data, avatar, banner);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdDoctor,
                        "Doctor profile and Keycloak account created. Login: " + createdDoctor.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorDto>> getDoctorById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(doctorService.getDoctorById(id)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DoctorDto>> updateDoctor(
            @PathVariable UUID id,
            @RequestPart("data") DoctorCreateRequest data,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar,
            @RequestPart(value = "banner", required = false) MultipartFile banner) {

        return ResponseEntity.ok(ApiResponse.success(doctorService.updateDoctor(id, data, avatar, banner)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateDoctor(@PathVariable UUID id) {
        doctorService.deactivateDoctor(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Doctor deactivated. Keycloak account disabled."));
    }
}
