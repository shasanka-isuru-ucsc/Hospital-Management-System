package com.hms.clinical.service;

import com.hms.clinical.dto.VitalsCreateRequest;
import com.hms.clinical.dto.VitalsDto;
import com.hms.clinical.entity.Session;
import com.hms.clinical.entity.Vitals;
import com.hms.clinical.exception.BusinessException;
import com.hms.clinical.repository.VitalsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VitalsServiceTest {

    @Mock
    private VitalsRepository vitalsRepository;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private VitalsService vitalsService;

    @Test
    void recordVitals_NewVitals_ShouldCreate() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setStatus("open");

        when(sessionService.getSessionEntity(sessionId)).thenReturn(session);
        when(vitalsRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        Vitals saved = new Vitals();
        saved.setId(UUID.randomUUID());
        saved.setSession(session);
        saved.setBpm(80);
        saved.setTemperature(37.5);
        saved.setRecordedAt(LocalDateTime.now());
        when(vitalsRepository.save(any(Vitals.class))).thenReturn(saved);

        VitalsCreateRequest request = VitalsCreateRequest.builder().bpm(80).temperature(37.5).build();
        VitalsDto result = vitalsService.recordVitals(sessionId, request);

        assertNotNull(result);
        assertEquals(80, result.getBpm());
        assertEquals(37.5, result.getTemperature());
    }

    @Test
    void recordVitals_ExistingVitals_ShouldUpdate() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setStatus("open");

        Vitals existing = new Vitals();
        existing.setId(UUID.randomUUID());
        existing.setSession(session);
        existing.setBpm(70);

        when(sessionService.getSessionEntity(sessionId)).thenReturn(session);
        when(vitalsRepository.findBySessionId(sessionId)).thenReturn(Optional.of(existing));
        when(vitalsRepository.save(any(Vitals.class))).thenAnswer(inv -> inv.getArgument(0));

        VitalsCreateRequest request = VitalsCreateRequest.builder().bpm(90).build();
        VitalsDto result = vitalsService.recordVitals(sessionId, request);

        assertEquals(90, result.getBpm());
    }

    @Test
    void recordVitals_CompletedSession_ShouldThrow() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setStatus("completed");

        when(sessionService.getSessionEntity(sessionId)).thenReturn(session);

        VitalsCreateRequest request = VitalsCreateRequest.builder().bpm(80).build();
        assertThrows(BusinessException.class, () -> vitalsService.recordVitals(sessionId, request));
    }

    @Test
    void getVitalsHistory_ShouldReturnList() {
        UUID patientId = UUID.randomUUID();

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setPatientId(patientId);
        session.setCreatedAt(LocalDateTime.now());

        Vitals v = new Vitals();
        v.setId(UUID.randomUUID());
        v.setSession(session);
        v.setBpm(75);
        v.setRecordedAt(LocalDateTime.now());

        when(vitalsRepository.findBySessionPatientIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                eq(patientId), any(), any())).thenReturn(List.of(v));

        List<VitalsDto> result = vitalsService.getVitalsHistory(patientId, null, null);
        assertEquals(1, result.size());
    }
}
