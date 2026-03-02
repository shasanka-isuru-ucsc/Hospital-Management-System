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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private SessionService sessionService;

    private UUID doctorId;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        doctorId = UUID.randomUUID();
        patientId = UUID.randomUUID();
    }

    @Test
    void createSession_ShouldReturnSessionDto() {
        SessionCreateRequest request = SessionCreateRequest.builder()
                .patientId(patientId)
                .sessionType("opd")
                .chiefComplaint("headache")
                .build();

        Session saved = new Session();
        saved.setId(UUID.randomUUID());
        saved.setPatientId(patientId);
        saved.setPatientName("Patient");
        saved.setDoctorId(doctorId);
        saved.setDoctorName("Dr. Smith");
        saved.setSessionType("opd");
        saved.setStatus("open");
        saved.setChiefComplaint("headache");

        when(sessionRepository.save(any(Session.class))).thenReturn(saved);

        SessionDto result = sessionService.createSession(request, doctorId, "Dr. Smith");

        assertNotNull(result);
        assertEquals(patientId, result.getPatientId());
        assertEquals("opd", result.getSessionType());
        assertEquals("open", result.getStatus());
        verify(sessionRepository, times(1)).save(any(Session.class));
    }

    @Test
    void getSession_WhenExists_ShouldReturnSessionDto() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setPatientId(patientId);
        session.setPatientName("Patient");
        session.setDoctorId(doctorId);
        session.setDoctorName("Dr. Smith");
        session.setSessionType("opd");
        session.setStatus("open");

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        SessionDto result = sessionService.getSession(sessionId);
        assertEquals(sessionId, result.getId());
    }

    @Test
    void getSession_WhenNotExists_ShouldThrowNotFound() {
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sessionService.getSession(sessionId));
    }

    @Test
    void listSessions_ShouldReturnPaginatedResults() {
        Session s1 = new Session();
        s1.setId(UUID.randomUUID());
        s1.setPatientId(patientId);
        s1.setPatientName("Patient");
        s1.setDoctorId(doctorId);
        s1.setDoctorName("Dr. Smith");
        s1.setSessionType("opd");
        s1.setStatus("open");

        Page<Session> page = new PageImpl<>(List.of(s1));
        when(sessionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<SessionDto> result = sessionService.listSessions(null, null, null, null, null, 1, 20);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void updateSession_WhenOpen_ShouldUpdateFields() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setPatientId(patientId);
        session.setPatientName("Patient");
        session.setDoctorId(doctorId);
        session.setDoctorName("Dr. Smith");
        session.setSessionType("opd");
        session.setStatus("open");
        session.setChiefComplaint("headache");

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenReturn(session);

        SessionUpdateRequest request = SessionUpdateRequest.builder()
                .diagnosis("Migraine")
                .build();

        SessionDto result = sessionService.updateSession(sessionId, request);
        assertEquals("Migraine", result.getDiagnosis());
    }

    @Test
    void updateSession_WhenCompleted_ShouldThrowBusinessException() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setStatus("completed");

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        SessionUpdateRequest request = SessionUpdateRequest.builder().diagnosis("Test").build();
        assertThrows(BusinessException.class, () -> sessionService.updateSession(sessionId, request));
    }

    @Test
    void completeSession_OPD_ShouldPublishBillingOpdEvent() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setPatientId(patientId);
        session.setPatientName("Patient");
        session.setDoctorId(doctorId);
        session.setDoctorName("Dr. Smith");
        session.setSessionType("opd");
        session.setStatus("open");

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionCompleteRequest request = SessionCompleteRequest.builder()
                .diagnosis("Typhoid")
                .discountPercent(0.0)
                .build();

        SessionDto result = sessionService.completeSession(sessionId, request);

        assertEquals("completed", result.getStatus());
        assertEquals("Typhoid", result.getDiagnosis());

        ArgumentCaptor<BillingEvent> captor = ArgumentCaptor.forClass(BillingEvent.class);
        verify(eventPublisher, times(1)).publishBillingOpdEvent(captor.capture());
        assertEquals(sessionId, captor.getValue().getSessionId());
    }

    @Test
    void completeSession_WoundCare_ShouldPublishBillingWoundEvent() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setPatientId(patientId);
        session.setPatientName("Patient");
        session.setDoctorId(doctorId);
        session.setDoctorName("Dr. Smith");
        session.setSessionType("wound_care");
        session.setStatus("open");

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionCompleteRequest request = SessionCompleteRequest.builder()
                .diagnosis("Deep laceration")
                .build();

        sessionService.completeSession(sessionId, request);

        verify(eventPublisher, times(1)).publishBillingWoundEvent(any(BillingEvent.class));
        verify(eventPublisher, never()).publishBillingOpdEvent(any());
    }

    @Test
    void completeSession_WithInternalPrescriptions_ShouldPublishPharmacyEvent() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setPatientId(patientId);
        session.setPatientName("Patient");
        session.setDoctorId(doctorId);
        session.setDoctorName("Dr. Smith");
        session.setSessionType("opd");
        session.setStatus("open");

        Prescription rx1 = new Prescription();
        rx1.setId(UUID.randomUUID());
        rx1.setSession(session);
        rx1.setType("internal");
        rx1.setMedicineName("Paracetamol");
        rx1.setDosage("500mg");
        rx1.setFrequency("3x daily");
        rx1.setDurationDays(5);
        rx1.setStatus("pending");

        Prescription rx2 = new Prescription();
        rx2.setId(UUID.randomUUID());
        rx2.setSession(session);
        rx2.setType("external");
        rx2.setMedicineName("Amoxicillin");
        rx2.setDosage("500mg");
        rx2.setFrequency("2x daily");
        rx2.setDurationDays(7);
        rx2.setStatus("dispensed");

        session.setPrescriptions(List.of(rx1, rx2));

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionCompleteRequest request = SessionCompleteRequest.builder()
                .diagnosis("Typhoid")
                .build();

        sessionService.completeSession(sessionId, request);

        // Billing event published
        verify(eventPublisher, times(1)).publishBillingOpdEvent(any(BillingEvent.class));

        // Pharmacy event published with only the internal prescription
        ArgumentCaptor<PharmacyNewRxEvent> captor = ArgumentCaptor.forClass(PharmacyNewRxEvent.class);
        verify(eventPublisher, times(1)).publishPharmacyNewRxEvent(captor.capture());
        PharmacyNewRxEvent pharmacyEvent = captor.getValue();
        assertEquals(sessionId, pharmacyEvent.getSessionId());
        assertEquals(1, pharmacyEvent.getPrescriptions().size());
        assertEquals("Paracetamol", pharmacyEvent.getPrescriptions().get(0).getMedicineName());
    }

    @Test
    void completeSession_WithoutInternalPrescriptions_ShouldNotPublishPharmacyEvent() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setPatientId(patientId);
        session.setPatientName("Patient");
        session.setDoctorId(doctorId);
        session.setDoctorName("Dr. Smith");
        session.setSessionType("opd");
        session.setStatus("open");
        // No prescriptions set (null)

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionCompleteRequest request = SessionCompleteRequest.builder()
                .diagnosis("Flu")
                .build();

        sessionService.completeSession(sessionId, request);

        verify(eventPublisher, times(1)).publishBillingOpdEvent(any(BillingEvent.class));
        verify(eventPublisher, never()).publishPharmacyNewRxEvent(any());
    }

    @Test
    void completeSession_OnlyExternalPrescriptions_ShouldNotPublishPharmacyEvent() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setPatientId(patientId);
        session.setPatientName("Patient");
        session.setDoctorId(doctorId);
        session.setDoctorName("Dr. Smith");
        session.setSessionType("opd");
        session.setStatus("open");

        Prescription external = new Prescription();
        external.setId(UUID.randomUUID());
        external.setSession(session);
        external.setType("external");
        external.setMedicineName("Amoxicillin");
        external.setStatus("dispensed");
        session.setPrescriptions(List.of(external));

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionCompleteRequest request = SessionCompleteRequest.builder()
                .diagnosis("Flu")
                .build();

        sessionService.completeSession(sessionId, request);

        verify(eventPublisher, never()).publishPharmacyNewRxEvent(any());
    }

    @Test
    void completeSession_AlreadyCompleted_ShouldThrow() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setStatus("completed");

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        SessionCompleteRequest request = SessionCompleteRequest.builder().diagnosis("Test").build();
        assertThrows(BusinessException.class, () -> sessionService.completeSession(sessionId, request));
    }
}
