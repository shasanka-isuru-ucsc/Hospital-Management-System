package com.hms.staff.controller;

import com.hms.staff.dto.ApiResponse;
import com.hms.staff.dto.DoctorCreateRequest;
import com.hms.staff.dto.DoctorDto;
import com.hms.staff.dto.DoctorUpdateRequest;
import com.hms.staff.dto.PaginationMeta;
import com.hms.staff.service.DoctorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DoctorDto>>> listDoctors(
            @RequestParam(required = false) UUID department_id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        Page<DoctorDto> result = doctorService.listDoctors(department_id, status, search, page, limit);
        PaginationMeta meta = new PaginationMeta(page, limit, result.getTotalElements(), result.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(result.getContent(), meta));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createDoctor(
            @RequestPart("first_name") String firstName,
            @RequestPart("last_name") String lastName,
            @RequestPart("username") String username,
            @RequestPart("email") String email,
            @RequestPart("mobile") String mobile,
            @RequestPart("password") String password,
            @RequestPart("date_of_birth") String dateOfBirth,
            @RequestPart("gender") String gender,
            @RequestPart("education") String education,
            @RequestPart("designation") String designation,
            @RequestPart("department_id") String departmentId,
            @RequestPart(value = "address", required = false) String address,
            @RequestPart(value = "city", required = false) String city,
            @RequestPart(value = "country", required = false) String country,
            @RequestPart(value = "state_province", required = false) String stateProvince,
            @RequestPart(value = "postal_code", required = false) String postalCode,
            @RequestPart(value = "biography", required = false) String biography,
            @RequestPart(value = "status", required = false) String status,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar,
            @RequestPart(value = "banner", required = false) MultipartFile banner) {

        DoctorCreateRequest request = new DoctorCreateRequest();
        request.setFirstName(firstName);
        request.setLastName(lastName);
        request.setUsername(username);
        request.setEmail(email);
        request.setMobile(mobile);
        request.setPassword(password);
        request.setDateOfBirth(java.time.LocalDate.parse(dateOfBirth));
        request.setGender(gender);
        request.setEducation(education);
        request.setDesignation(designation);
        request.setDepartmentId(UUID.fromString(departmentId));
        request.setAddress(address);
        request.setCity(city);
        request.setCountry(country);
        request.setStateProvince(stateProvince);
        request.setPostalCode(postalCode);
        request.setBiography(biography);
        request.setStatus(status);

        DoctorDto created = doctorService.createDoctor(request, avatar, banner);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", created);
        response.put("message", "Doctor profile and Keycloak account created. Login: " + created.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorDto>> getDoctorById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(doctorService.getDoctorById(id)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DoctorDto>> updateDoctor(
            @PathVariable UUID id,
            @RequestPart(value = "first_name", required = false) String firstName,
            @RequestPart(value = "last_name", required = false) String lastName,
            @RequestPart(value = "email", required = false) String email,
            @RequestPart(value = "mobile", required = false) String mobile,
            @RequestPart(value = "education", required = false) String education,
            @RequestPart(value = "designation", required = false) String designation,
            @RequestPart(value = "department_id", required = false) String departmentId,
            @RequestPart(value = "address", required = false) String address,
            @RequestPart(value = "city", required = false) String city,
            @RequestPart(value = "country", required = false) String country,
            @RequestPart(value = "state_province", required = false) String stateProvince,
            @RequestPart(value = "postal_code", required = false) String postalCode,
            @RequestPart(value = "biography", required = false) String biography,
            @RequestPart(value = "status", required = false) String status,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar,
            @RequestPart(value = "banner", required = false) MultipartFile banner) {

        DoctorUpdateRequest request = new DoctorUpdateRequest();
        request.setFirstName(firstName);
        request.setLastName(lastName);
        request.setEmail(email);
        request.setMobile(mobile);
        request.setEducation(education);
        request.setDesignation(designation);
        if (departmentId != null) request.setDepartmentId(UUID.fromString(departmentId));
        request.setAddress(address);
        request.setCity(city);
        request.setCountry(country);
        request.setStateProvince(stateProvince);
        request.setPostalCode(postalCode);
        request.setBiography(biography);
        request.setStatus(status);

        return ResponseEntity.ok(ApiResponse.success(doctorService.updateDoctor(id, request, avatar, banner)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deactivateDoctor(@PathVariable UUID id) {
        doctorService.deactivateDoctor(id);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Doctor deactivated. Keycloak account disabled.");
        return ResponseEntity.ok(response);
    }
}
