package com.hms.reception.controller;

import com.hms.reception.dto.ApiResponse;
import com.hms.reception.dto.PaginationMeta;
import com.hms.reception.dto.PatientCreateRequest;
import com.hms.reception.entity.Patient;
import com.hms.reception.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @PostMapping
    public ResponseEntity<ApiResponse<Patient>> registerPatient(
            @Valid @RequestBody PatientCreateRequest request) {

        Patient patient = Patient.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .mobile(request.getMobile())
                .email(request.getEmail())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .address(request.getAddress())
                .bloodGroup(request.getBloodGroup())
                .triage(request.getTriage())
                .build();

        Patient created = patientService.registerPatient(patient);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Patient>>> getAllPatients(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        Pageable pageable = PageRequest.of(page - 1, limit);
        Page<Patient> patientsPage = patientService.getAllPatients(search, status, pageable);

        PaginationMeta meta = new PaginationMeta(page, limit, patientsPage.getTotalElements(),
                patientsPage.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(patientsPage.getContent(), meta));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> searchPatients(@RequestParam String q) {
        List<Patient> patients = patientService.searchPatients(q);

        List<Map<String, Object>> lightweightList = patients.stream().map(p -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", p.getId());
            map.put("patientNumber", p.getPatientNumber());
            map.put("fullName", p.getFirstName() + " " + p.getLastName());
            map.put("mobile", p.getMobile());
            map.put("dateOfBirth", p.getDateOfBirth());
            map.put("gender", p.getGender());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(lightweightList));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Patient>> getPatientById(@PathVariable UUID id) {
        Patient patient = patientService.getPatientById(id);
        return ResponseEntity.ok(ApiResponse.success(patient));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Patient>> updatePatient(
            @PathVariable UUID id,
            @RequestBody Patient updateData) {

        Patient updated = patientService.updatePatient(id, updateData);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }
}
