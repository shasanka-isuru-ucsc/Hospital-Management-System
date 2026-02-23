package com.hms.reception.controller;

import com.hms.reception.dto.ApiResponse;
import com.hms.reception.dto.AppointmentCreateRequest;
import com.hms.reception.dto.AppointmentUpdateRequest;
import com.hms.reception.dto.PaginationMeta;
import com.hms.reception.entity.Appointment;
import com.hms.reception.entity.Patient;
import com.hms.reception.service.AppointmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    public ResponseEntity<ApiResponse<Appointment>> bookAppointment(
            @Valid @RequestBody AppointmentCreateRequest request,
            HttpServletRequest httpRequest) {

        String userIdStr = (String) httpRequest.getAttribute("userId");
        UUID bookedBy = userIdStr != null ? UUID.fromString(userIdStr) : null;

        Appointment appointment = Appointment.builder()
                .doctorId(request.getDoctorId())
                .appointmentDate(request.getAppointmentDate())
                .fromTime(request.getFromTime())
                .toTime(request.getToTime())
                .treatment(request.getTreatment())
                .notes(request.getNotes())
                .build();

        if (request.getPatientId() != null) {
            Patient patientRef = new Patient();
            patientRef.setId(request.getPatientId());
            appointment.setPatient(patientRef);
        } else {
            appointment.setPatientName(request.getPatientName());
            appointment.setPatientMobile(request.getPatientMobile());
            appointment.setGender(request.getGender());
            appointment.setAddress(request.getAddress());
        }

        Appointment created = appointmentService.bookAppointment(appointment, bookedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Appointment>>> getAppointments(
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest httpRequest) {

        String userRole = (String) httpRequest.getAttribute("userRole");
        String userIdStr = (String) httpRequest.getAttribute("userId");

        // If user is doctor, force filter by their own ID.
        if ("doctor".equalsIgnoreCase(userRole) && userIdStr != null) {
            doctorId = UUID.fromString(userIdStr);
        }

        Pageable pageable = PageRequest.of(page - 1, limit);
        Page<Appointment> appointmentsPage = appointmentService.getAppointments(doctorId, patientId, date, status,
                pageable);

        PaginationMeta meta = new PaginationMeta(page, limit, appointmentsPage.getTotalElements(),
                appointmentsPage.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(appointmentsPage.getContent(), meta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Appointment>> getAppointmentById(@PathVariable UUID id) {
        Appointment appointment = appointmentService.getAppointmentById(id);
        return ResponseEntity.ok(ApiResponse.success(appointment));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Appointment>> updateAppointment(
            @PathVariable UUID id,
            @RequestBody AppointmentUpdateRequest request) {

        Appointment updateData = new Appointment();
        updateData.setDoctorId(request.getDoctorId());
        updateData.setAppointmentDate(request.getAppointmentDate());
        if (request.getFromTime() != null)
            updateData.setFromTime(LocalTime.parse(request.getFromTime()));
        if (request.getToTime() != null)
            updateData.setToTime(LocalTime.parse(request.getToTime()));
        updateData.setTreatment(request.getTreatment());
        updateData.setNotes(request.getNotes());
        updateData.setStatus(request.getStatus());

        Appointment updated = appointmentService.updateAppointment(id, updateData);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelAppointment(@PathVariable UUID id) {
        appointmentService.cancelAppointment(id);
        return ResponseEntity.noContent().build();
    }
}
