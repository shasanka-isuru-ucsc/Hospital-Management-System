package com.hms.clinical.service;

import com.hms.clinical.dto.PrescriptionCreateRequest;
import com.hms.clinical.dto.PrescriptionDto;
import com.hms.clinical.entity.Prescription;
import com.hms.clinical.entity.Session;
import com.hms.clinical.exception.BusinessException;
import com.hms.clinical.exception.ResourceNotFoundException;
import com.hms.clinical.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final SessionService sessionService;

    @Transactional
    public List<PrescriptionDto> addPrescriptions(UUID sessionId, PrescriptionCreateRequest request) {
        Session session = sessionService.getSessionEntity(sessionId);

        if ("completed".equals(session.getStatus())) {
            throw new BusinessException("Cannot add prescriptions to a completed session");
        }

        List<Prescription> prescriptions = request.getPrescriptions().stream().map(item -> {
            Prescription rx = new Prescription();
            rx.setSession(session);
            rx.setType(item.getType());
            rx.setMedicineName(item.getMedicineName());
            rx.setDosage(item.getDosage());
            rx.setFrequency(item.getFrequency());
            rx.setDurationDays(item.getDurationDays());
            rx.setInstructions(item.getInstructions());
            rx.setPharmacyName(item.getPharmacyName());
            rx.setStatus("internal".equals(item.getType()) ? "pending" : "dispensed");
            return rx;
        }).collect(Collectors.toList());

        List<Prescription> saved = prescriptionRepository.saveAll(prescriptions);
        return saved.stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<PrescriptionDto> getPrescriptions(UUID sessionId, String type) {
        List<Prescription> prescriptions;
        if (type != null) {
            prescriptions = prescriptionRepository.findBySessionIdAndType(sessionId, type);
        } else {
            prescriptions = prescriptionRepository.findBySessionId(sessionId);
        }
        return prescriptions.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public void deletePrescription(UUID sessionId, UUID rxId) {
        Session session = sessionService.getSessionEntity(sessionId);
        if ("completed".equals(session.getStatus())) {
            throw new BusinessException("Cannot delete prescriptions from a completed session");
        }

        Prescription rx = prescriptionRepository.findById(rxId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found: " + rxId));
        if (!rx.getSession().getId().equals(sessionId)) {
            throw new ResourceNotFoundException("Prescription does not belong to session: " + sessionId);
        }
        prescriptionRepository.delete(rx);
    }

    public List<PrescriptionDto> getPharmacyQueue(String status) {
        String filterStatus = (status != null) ? status : "pending";
        return prescriptionRepository.findPharmacyQueue(filterStatus).stream()
                .map(p -> {
                    PrescriptionDto dto = toDto(p);
                    dto.setPatientName(p.getSession().getPatientName());
                    dto.setSessionDate(p.getSession().getCreatedAt().toLocalDate());
                    dto.setDoctorName(p.getSession().getDoctorName());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public PrescriptionDto dispensePrescription(UUID rxId) {
        Prescription rx = prescriptionRepository.findById(rxId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found: " + rxId));

        if (!"internal".equals(rx.getType())) {
            throw new BusinessException("Only internal prescriptions can be dispensed");
        }
        if ("dispensed".equals(rx.getStatus())) {
            throw new BusinessException("Prescription is already dispensed");
        }

        rx.setStatus("dispensed");
        Prescription saved = prescriptionRepository.save(rx);
        return toDto(saved);
    }

    private PrescriptionDto toDto(Prescription p) {
        return PrescriptionDto.builder()
                .id(p.getId())
                .sessionId(p.getSession().getId())
                .type(p.getType())
                .medicineName(p.getMedicineName())
                .dosage(p.getDosage())
                .frequency(p.getFrequency())
                .durationDays(p.getDurationDays())
                .instructions(p.getInstructions())
                .pharmacyName(p.getPharmacyName())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
