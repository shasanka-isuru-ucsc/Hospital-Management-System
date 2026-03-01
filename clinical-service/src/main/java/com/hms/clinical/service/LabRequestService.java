package com.hms.clinical.service;

import com.hms.clinical.dto.LabRequestCreateRequest;
import com.hms.clinical.dto.LabRequestDto;
import com.hms.clinical.entity.LabRequest;
import com.hms.clinical.entity.Session;
import com.hms.clinical.event.EventPublisher;
import com.hms.clinical.event.LabRequestedEvent;
import com.hms.clinical.exception.BusinessException;
import com.hms.clinical.repository.LabRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LabRequestService {

    private final LabRequestRepository labRequestRepository;
    private final SessionService sessionService;
    private final EventPublisher eventPublisher;

    @Transactional
    public List<LabRequestDto> createLabRequests(UUID sessionId, LabRequestCreateRequest request) {
        Session session = sessionService.getSessionEntity(sessionId);

        if ("completed".equals(session.getStatus())) {
            throw new BusinessException("Cannot request lab tests for a completed session");
        }

        List<LabRequest> labRequests = request.getTests().stream().map(item -> {
            LabRequest lr = new LabRequest();
            lr.setSession(session);
            lr.setTestId(item.getTestId());
            lr.setTestName("Lab Test"); // placeholder — ideally fetched from lab catalog
            lr.setUrgency(item.getUrgency() != null ? item.getUrgency() : "routine");
            return lr;
        }).collect(Collectors.toList());

        List<LabRequest> saved = labRequestRepository.saveAll(labRequests);

        // Publish lab.requested event
        LabRequestedEvent event = LabRequestedEvent.builder()
                .sessionId(session.getId())
                .patientId(session.getPatientId())
                .patientName(session.getPatientName())
                .doctorId(session.getDoctorId())
                .doctorName(session.getDoctorName())
                .notes(request.getNotes())
                .tests(saved.stream().map(lr -> LabRequestedEvent.TestItem.builder()
                        .requestId(lr.getId())
                        .testId(lr.getTestId())
                        .testName(lr.getTestName())
                        .urgency(lr.getUrgency())
                        .requestedAt(lr.getRequestedAt())
                        .build()).collect(Collectors.toList()))
                .build();

        eventPublisher.publishLabRequestedEvent(event);

        return saved.stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<LabRequestDto> getLabRequests(UUID sessionId) {
        return labRequestRepository.findBySessionId(sessionId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private LabRequestDto toDto(LabRequest lr) {
        return LabRequestDto.builder()
                .id(lr.getId())
                .sessionId(lr.getSession().getId())
                .testId(lr.getTestId())
                .testName(lr.getTestName())
                .urgency(lr.getUrgency())
                .labOrderId(lr.getLabOrderId())
                .orderStatus(lr.getOrderStatus())
                .requestedAt(lr.getRequestedAt())
                .build();
    }
}
