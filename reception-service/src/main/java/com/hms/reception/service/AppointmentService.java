package com.hms.reception.service;

import com.hms.reception.entity.Appointment;
import com.hms.reception.entity.Patient;
import com.hms.reception.exception.BusinessException;
import com.hms.reception.exception.ResourceNotFoundException;
import com.hms.reception.repository.AppointmentRepository;
import com.hms.reception.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final MinioService minioService;

    @Transactional
    public Appointment bookAppointment(Appointment appointmentDTO, UUID bookedBy) {
        log.info("Booking appointment for doctor {} on date {}", appointmentDTO.getDoctorId(),
                appointmentDTO.getAppointmentDate());

        if (appointmentDTO.getPatient() != null && appointmentDTO.getPatient().getId() != null) {
            Patient patient = patientRepository.findById(appointmentDTO.getPatient().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Patient not found: " + appointmentDTO.getPatient().getId()));
            appointmentDTO.setPatient(patient);
            appointmentDTO.setPatientName(patient.getFirstName() + " " + patient.getLastName());
            appointmentDTO.setPatientMobile(patient.getMobile());
        }

        checkDoctorAvailability(appointmentDTO.getDoctorId(), appointmentDTO.getAppointmentDate(),
                appointmentDTO.getFromTime(), appointmentDTO.getToTime(), null);

        appointmentDTO.setStatus("scheduled");
        appointmentDTO.setBookedBy(bookedBy);

        return appointmentRepository.save(appointmentDTO);
    }

    public Page<Appointment> getAppointments(UUID doctorId, UUID patientId, LocalDate date, String status,
            Pageable pageable) {

        log.info("repo filters doctorId={}, patientId={}, date={}, status={}",
                doctorId, patientId, date, status);
        Page<Appointment> appointments = appointmentRepository.findWithFilters(doctorId, patientId, date, status,
                pageable);
        appointments.forEach(this::enrichWithPresignedUrl);
        return appointments;
    }

    public Appointment getAppointmentById(UUID id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));
        enrichWithPresignedUrl(appointment);
        return appointment;
    }

    @Transactional
    public Appointment updateAppointment(UUID id, Appointment updateData) {
        Appointment appointment = getAppointmentById(id);

        boolean checkAvailability = false;
        if (updateData.getDoctorId() != null && !updateData.getDoctorId().equals(appointment.getDoctorId())) {
            appointment.setDoctorId(updateData.getDoctorId());
            checkAvailability = true;
        }
        if (updateData.getAppointmentDate() != null
                && !updateData.getAppointmentDate().equals(appointment.getAppointmentDate())) {
            appointment.setAppointmentDate(updateData.getAppointmentDate());
            checkAvailability = true;
        }
        if (updateData.getFromTime() != null && !updateData.getFromTime().equals(appointment.getFromTime())) {
            appointment.setFromTime(updateData.getFromTime());
            checkAvailability = true;
        }
        if (updateData.getToTime() != null && !updateData.getToTime().equals(appointment.getToTime())) {
            appointment.setToTime(updateData.getToTime());
            checkAvailability = true;
        }

        if (checkAvailability) {
            checkDoctorAvailability(appointment.getDoctorId(), appointment.getAppointmentDate(),
                    appointment.getFromTime(), appointment.getToTime(), id);
        }

        if (updateData.getTreatment() != null)
            appointment.setTreatment(updateData.getTreatment());
        if (updateData.getNotes() != null)
            appointment.setNotes(updateData.getNotes());
        if (updateData.getStatus() != null)
            appointment.setStatus(updateData.getStatus());

        return appointmentRepository.save(appointment);
    }

    @Transactional
    public void cancelAppointment(UUID id) {
        Appointment appointment = getAppointmentById(id);
        appointment.setStatus("cancelled");
        appointmentRepository.save(appointment);
    }

    private void checkDoctorAvailability(UUID doctorId, LocalDate date, LocalTime fromTime, LocalTime toTime,
            UUID excludeId) {
        List<Appointment> overlapping = appointmentRepository.findOverlappingAppointments(doctorId, date, fromTime,
                toTime, excludeId);
        if (!overlapping.isEmpty()) {
            throw new BusinessException("SLOT_NOT_AVAILABLE",
                    "Doctor has overlapping appointments at the requested time", 422);
        }
    }

    private void enrichWithPresignedUrl(Appointment appointment) {
        if (appointment.getAvatarUrl() != null && !appointment.getAvatarUrl().startsWith("http")) {
            appointment.setAvatarUrl(minioService.getPresignedUrl(appointment.getAvatarUrl()));
        }
    }
}
