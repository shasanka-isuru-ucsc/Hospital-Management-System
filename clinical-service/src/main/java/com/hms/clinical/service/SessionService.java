package com.hms.clinical.service;

import com.hms.clinical.dto.*;
import com.hms.clinical.entity.Prescription;
import com.hms.clinical.entity.Session;
import com.hms.clinical.event.BillingEvent;
import com.hms.clinical.event.EventPublisher;
import com.hms.clinical.event.PharmacyNewRxEvent;
import com.hms.clinical.exception.BusinessException;
import com.hms.clinical.exception.ResourceNotFoundException;
import com.hms.clinical.repository.SessionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final SessionRepository sessionRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public SessionDto createSession(SessionCreateRequest request, UUID doctorId, String doctorName) {
        Session session = new Session();
        session.setPatientId(request.getPatientId());
        session.setPatientName("Patient"); // placeholder — ideally fetched from reception service
        session.setDoctorId(doctorId);
        session.setDoctorName(doctorName);
        session.setSessionType(request.getSessionType());
        session.setAppointmentId(request.getAppointmentId());
        session.setChiefComplaint(request.getChiefComplaint());
        session.setStatus("open");

        Session saved = sessionRepository.save(session);
        return toDto(saved);
    }

    public SessionDto getSession(UUID id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));
        return toDto(session);
    }

    public Page<SessionDto> listSessions(UUID patientId, UUID doctorId, String sessionType,
            String status, LocalDate date, int page, int limit) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Session> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (patientId != null)
                predicates.add(cb.equal(root.get("patientId"), patientId));
            if (doctorId != null)
                predicates.add(cb.equal(root.get("doctorId"), doctorId));
            if (sessionType != null)
                predicates.add(cb.equal(root.get("sessionType"), sessionType));
            if (status != null)
                predicates.add(cb.equal(root.get("status"), status));
            if (date != null) {
                predicates.add(cb.between(root.get("createdAt"),
                        date.atStartOfDay(), date.plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return sessionRepository.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional
    public SessionDto updateSession(UUID id, SessionUpdateRequest request) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));

        if ("completed".equals(session.getStatus())) {
            throw new BusinessException("Cannot update a completed session");
        }

        if (request.getChiefComplaint() != null)
            session.setChiefComplaint(request.getChiefComplaint());
        if (request.getDiagnosis() != null)
            session.setDiagnosis(request.getDiagnosis());
        if (request.getFollowUpDate() != null)
            session.setFollowUpDate(request.getFollowUpDate());
        if (request.getNotes() != null)
            session.setNotes(request.getNotes());

        Session saved = sessionRepository.save(session);
        return toDto(saved);
    }

    @Transactional
    public SessionDto completeSession(UUID id, SessionCompleteRequest request) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));

        if ("completed".equals(session.getStatus())) {
            throw new BusinessException("Session is already completed");
        }

        session.setDiagnosis(request.getDiagnosis());
        session.setStatus("completed");
        session.setCompletedAt(LocalDateTime.now());

        if (request.getDiscountPercent() != null) {
            session.setDiscountPercent(request.getDiscountPercent());
        }
        if (request.getDiscountReason() != null) {
            session.setDiscountReason(request.getDiscountReason());
        }

        Session saved = sessionRepository.save(session);

        // Publish billing event
        BillingEvent billingEvent = BillingEvent.builder()
                .sessionId(saved.getId())
                .patientId(saved.getPatientId())
                .patientName(saved.getPatientName())
                .sessionType(saved.getSessionType())
                .doctorId(saved.getDoctorId())
                .doctorName(saved.getDoctorName())
                .consultationFee(request.getConsultationFee())
                .discountPercent(saved.getDiscountPercent())
                .discountReason(saved.getDiscountReason())
                .completedAt(saved.getCompletedAt())
                .build();

        if ("opd".equals(saved.getSessionType())) {
            eventPublisher.publishBillingOpdEvent(billingEvent);
        } else if ("wound_care".equals(saved.getSessionType())) {
            eventPublisher.publishBillingWoundEvent(billingEvent);
        }

        // Publish pharmacy event for internal prescriptions (only internal type, pending status)
        List<Prescription> internalRxs = (saved.getPrescriptions() != null)
                ? saved.getPrescriptions().stream()
                        .filter(p -> "internal".equals(p.getType()))
                        .collect(Collectors.toList())
                : List.of();

        if (!internalRxs.isEmpty()) {
            PharmacyNewRxEvent pharmacyEvent = PharmacyNewRxEvent.builder()
                    .sessionId(saved.getId())
                    .patientId(saved.getPatientId())
                    .patientName(saved.getPatientName())
                    .doctorName(saved.getDoctorName())
                    .completedAt(saved.getCompletedAt())
                    .prescriptions(internalRxs.stream()
                            .map(p -> PharmacyNewRxEvent.RxItem.builder()
                                    .id(p.getId())
                                    .medicineName(p.getMedicineName())
                                    .dosage(p.getDosage())
                                    .frequency(p.getFrequency())
                                    .durationDays(p.getDurationDays())
                                    .instructions(p.getInstructions())
                                    .build())
                            .collect(Collectors.toList()))
                    .build();
            eventPublisher.publishPharmacyNewRxEvent(pharmacyEvent);
        }

        return toDto(saved);
    }

    public Session getSessionEntity(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));
    }

    public SessionDto toDto(Session s) {
        SessionDto dto = SessionDto.builder()
                .id(s.getId())
                .patientId(s.getPatientId())
                .patientName(s.getPatientName())
                .doctorId(s.getDoctorId())
                .doctorName(s.getDoctorName())
                .nurseId(s.getNurseId())
                .sessionType(s.getSessionType())
                .appointmentId(s.getAppointmentId())
                .chiefComplaint(s.getChiefComplaint())
                .diagnosis(s.getDiagnosis())
                .followUpDate(s.getFollowUpDate())
                .status(s.getStatus())
                .discountPercent(s.getDiscountPercent())
                .discountReason(s.getDiscountReason())
                .completedAt(s.getCompletedAt())
                .createdAt(s.getCreatedAt())
                .build();

        if (s.getVitals() != null) {
            dto.setVitals(VitalsDto.builder()
                    .id(s.getVitals().getId())
                    .sessionId(s.getId())
                    .bpm(s.getVitals().getBpm())
                    .temperature(s.getVitals().getTemperature())
                    .bloodPressureSys(s.getVitals().getBloodPressureSys())
                    .bloodPressureDia(s.getVitals().getBloodPressureDia())
                    .spo2(s.getVitals().getSpo2())
                    .weightKg(s.getVitals().getWeightKg())
                    .heightCm(s.getVitals().getHeightCm())
                    .bloodSugar(s.getVitals().getBloodSugar())
                    .recordedAt(s.getVitals().getRecordedAt())
                    .build());
        }

        if (s.getPrescriptions() != null) {
            dto.setPrescriptions(s.getPrescriptions().stream()
                    .map(p -> PrescriptionDto.builder()
                            .id(p.getId())
                            .sessionId(s.getId())
                            .type(p.getType())
                            .medicineName(p.getMedicineName())
                            .dosage(p.getDosage())
                            .frequency(p.getFrequency())
                            .durationDays(p.getDurationDays())
                            .instructions(p.getInstructions())
                            .pharmacyName(p.getPharmacyName())
                            .status(p.getStatus())
                            .createdAt(p.getCreatedAt())
                            .build())
                    .collect(Collectors.toList()));
        }

        if (s.getImages() != null) {
            dto.setImages(s.getImages().stream()
                    .map(i -> SessionImageDto.builder()
                            .id(i.getId())
                            .sessionId(s.getId())
                            .fileUrl(i.getFileUrl())
                            .caption(i.getCaption())
                            .imageType(i.getImageType())
                            .uploadedAt(i.getUploadedAt())
                            .build())
                    .collect(Collectors.toList()));
        }

        if (s.getLabRequests() != null) {
            dto.setLabRequests(s.getLabRequests().stream()
                    .map(l -> LabRequestDto.builder()
                            .id(l.getId())
                            .sessionId(s.getId())
                            .testId(l.getTestId())
                            .testName(l.getTestName())
                            .urgency(l.getUrgency())
                            .labOrderId(l.getLabOrderId())
                            .orderStatus(l.getOrderStatus())
                            .requestedAt(l.getRequestedAt())
                            .build())
                    .collect(Collectors.toList()));
        }

        return dto;
    }
}
