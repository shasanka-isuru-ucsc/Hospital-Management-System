package com.hms.reception.service;

import com.hms.reception.entity.Patient;
import com.hms.reception.entity.Token;
import com.hms.reception.event.QueueEventPublisher;
import com.hms.reception.event.QueueUpdatedEvent;
import com.hms.reception.exception.BusinessException;
import com.hms.reception.exception.ResourceNotFoundException;
import com.hms.reception.repository.PatientRepository;
import com.hms.reception.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenRepository tokenRepository;
    private final PatientRepository patientRepository;
    private final QueueEventPublisher eventPublisher;

    @Transactional
    public Token issueToken(UUID patientId, String queueType, UUID doctorId, UUID issuedBy) {
        log.info("Issuing token for patient {} in queue {}", patientId, queueType);

        if ("doctor_consultation".equals(queueType) && doctorId == null) {
            throw new BusinessException("MISSING_DOCTOR", "doctor_id is required for doctor_consultation queue", 400);
        }

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + patientId));

        LocalDate today = LocalDate.now();

        boolean hasActiveToken = tokenRepository.existsByPatientIdAndQueueTypeAndSessionDateAndStatusNot(
                patientId, queueType, today, "cancelled");
        if (hasActiveToken) {
            throw new BusinessException("DUPLICATE_TOKEN", "Patient already has an active token for this queue today",
                    422);
        }

        // Token number logic: resets daily per queue type
        Integer maxToken = tokenRepository.findMaxTokenNumber(queueType, today);
        int nextTokenNumber = (maxToken == null ? 0 : maxToken) + 1;

        Token token = Token.builder()
                .patient(patient)
                .queueType(queueType)
                .doctorId(doctorId)
                .tokenNumber(nextTokenNumber)
                .status("waiting")
                .sessionDate(today)
                .issuedBy(issuedBy)
                .issuedAt(ZonedDateTime.now())
                .build();

        token = tokenRepository.save(token);

        publishQueueEvent(token);

        return token;
    }

    public List<Token> getTodayTokens() {
        return tokenRepository.findBySessionDate(LocalDate.now());
    }

    public List<Token> getQueueTokens(String queueType, UUID doctorId) {
        LocalDate today = LocalDate.now();
        if ("doctor_consultation".equals(queueType) && doctorId != null) {
            return tokenRepository.findByQueueTypeAndDoctorIdAndSessionDateOrderByTokenNumberAsc(queueType, doctorId,
                    today);
        }
        return tokenRepository.findByQueueTypeAndSessionDateOrderByTokenNumberAsc(queueType, today);
    }

    @Transactional
    public Token updateTokenStatus(UUID tokenId, String status) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found: " + tokenId));

        token.setStatus(status);
        ZonedDateTime now = ZonedDateTime.now();
        if ("called".equals(status)) {
            token.setCalledAt(now);
        } else if ("completed".equals(status)) {
            token.setCompletedAt(now);
        }

        token = tokenRepository.save(token);
        publishQueueEvent(token);
        return token;
    }

    public long getQueuePosition(Token token) {
        return tokenRepository.countWaitingBeforeToken(token.getQueueType(), token.getSessionDate(),
                token.getTokenNumber());
    }

    private void publishQueueEvent(Token token) {
        QueueUpdatedEvent event = QueueUpdatedEvent.builder()
                .tokenId(token.getId())
                .tokenNumber(token.getTokenNumber())
                .patientName(token.getPatient().getFirstName() + " " + token.getPatient().getLastName())
                .queueType(token.getQueueType())
                .doctorId(token.getDoctorId())
                .status(token.getStatus())
                .updatedAt(ZonedDateTime.now().toLocalDateTime())
                .build();
        eventPublisher.publishQueueUpdated(event);
    }
}
