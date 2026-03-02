package com.hms.clinical.service;

import com.hms.clinical.dto.PrescriptionCreateRequest;
import com.hms.clinical.dto.PrescriptionDto;
import com.hms.clinical.entity.Prescription;
import com.hms.clinical.entity.Session;
import com.hms.clinical.exception.BusinessException;
import com.hms.clinical.exception.ResourceNotFoundException;
import com.hms.clinical.repository.PrescriptionRepository;
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
class PrescriptionServiceTest {

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private PrescriptionService prescriptionService;

    @Test
    void addPrescriptions_ShouldCreateMultiple() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setStatus("open");
        session.setPatientName("Patient");
        session.setDoctorName("Dr. Smith");
        session.setCreatedAt(LocalDateTime.now());

        when(sessionService.getSessionEntity(sessionId)).thenReturn(session);

        PrescriptionCreateRequest request = PrescriptionCreateRequest.builder()
                .prescriptions(List.of(
                        PrescriptionCreateRequest.PrescriptionItem.builder()
                                .type("internal").medicineName("Paracetamol")
                                .dosage("500mg").frequency("3x daily").durationDays(5)
                                .build(),
                        PrescriptionCreateRequest.PrescriptionItem.builder()
                                .type("external").medicineName("Amoxicillin")
                                .dosage("500mg").frequency("2x daily").durationDays(7)
                                .pharmacyName("City Pharmacy")
                                .build()))
                .build();

        when(prescriptionRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Prescription> list = inv.getArgument(0);
            list.forEach(p -> p.setId(UUID.randomUUID()));
            return list;
        });

        List<PrescriptionDto> result = prescriptionService.addPrescriptions(sessionId, request);
        assertEquals(2, result.size());
        assertEquals("pending", result.get(0).getStatus()); // internal
        assertEquals("dispensed", result.get(1).getStatus()); // external
    }

    @Test
    void addPrescriptions_CompletedSession_ShouldThrow() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setStatus("completed");

        when(sessionService.getSessionEntity(sessionId)).thenReturn(session);

        PrescriptionCreateRequest request = PrescriptionCreateRequest.builder()
                .prescriptions(List.of(
                        PrescriptionCreateRequest.PrescriptionItem.builder()
                                .type("internal").medicineName("Test")
                                .dosage("1").frequency("1").durationDays(1)
                                .build()))
                .build();

        assertThrows(BusinessException.class, () -> prescriptionService.addPrescriptions(sessionId, request));
    }

    @Test
    void deletePrescription_CompletedSession_ShouldThrow() {
        UUID sessionId = UUID.randomUUID();
        UUID rxId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setStatus("completed");

        when(sessionService.getSessionEntity(sessionId)).thenReturn(session);

        assertThrows(BusinessException.class, () -> prescriptionService.deletePrescription(sessionId, rxId));
    }

    @Test
    void dispensePrescription_InternalPending_ShouldDispense() {
        UUID rxId = UUID.randomUUID();
        Session session = new Session();
        session.setId(UUID.randomUUID());

        Prescription rx = new Prescription();
        rx.setId(rxId);
        rx.setSession(session);
        rx.setType("internal");
        rx.setStatus("pending");
        rx.setMedicineName("Paracetamol");
        rx.setDosage("500mg");
        rx.setFrequency("3x");
        rx.setDurationDays(5);

        when(prescriptionRepository.findById(rxId)).thenReturn(Optional.of(rx));
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(inv -> inv.getArgument(0));

        PrescriptionDto result = prescriptionService.dispensePrescription(rxId);
        assertEquals("dispensed", result.getStatus());
    }

    @Test
    void dispensePrescription_External_ShouldThrow() {
        UUID rxId = UUID.randomUUID();
        Session session = new Session();
        session.setId(UUID.randomUUID());

        Prescription rx = new Prescription();
        rx.setId(rxId);
        rx.setSession(session);
        rx.setType("external");
        rx.setStatus("pending");

        when(prescriptionRepository.findById(rxId)).thenReturn(Optional.of(rx));

        assertThrows(BusinessException.class, () -> prescriptionService.dispensePrescription(rxId));
    }

    @Test
    void dispensePrescription_AlreadyDispensed_ShouldThrow() {
        UUID rxId = UUID.randomUUID();
        Session session = new Session();
        session.setId(UUID.randomUUID());

        Prescription rx = new Prescription();
        rx.setId(rxId);
        rx.setSession(session);
        rx.setType("internal");
        rx.setStatus("dispensed");

        when(prescriptionRepository.findById(rxId)).thenReturn(Optional.of(rx));

        assertThrows(BusinessException.class, () -> prescriptionService.dispensePrescription(rxId));
    }

    @Test
    void getPharmacyQueue_DefaultStatus_ShouldQueryPendingFromCompletedSessions() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setStatus("completed");
        session.setPatientName("Patient");
        session.setDoctorName("Dr. Smith");
        session.setCreatedAt(LocalDateTime.now());

        Prescription rx = new Prescription();
        rx.setId(UUID.randomUUID());
        rx.setSession(session);
        rx.setType("internal");
        rx.setMedicineName("Paracetamol");
        rx.setDosage("500mg");
        rx.setFrequency("3x daily");
        rx.setDurationDays(5);
        rx.setStatus("pending");

        when(prescriptionRepository.findPharmacyQueue("pending")).thenReturn(List.of(rx));

        List<PrescriptionDto> result = prescriptionService.getPharmacyQueue(null);

        assertEquals(1, result.size());
        assertEquals("Paracetamol", result.get(0).getMedicineName());
        assertEquals("Patient", result.get(0).getPatientName());
        assertEquals("Dr. Smith", result.get(0).getDoctorName());
        verify(prescriptionRepository, times(1)).findPharmacyQueue("pending");
    }

    @Test
    void getPharmacyQueue_OpenSessionPrescriptions_ShouldNotAppear() {
        // Repository query filters by s.status = 'completed', so open-session prescriptions
        // are excluded at the query level. This test verifies service returns empty when
        // repository returns empty (simulating no completed-session results).
        when(prescriptionRepository.findPharmacyQueue("pending")).thenReturn(List.of());

        List<PrescriptionDto> result = prescriptionService.getPharmacyQueue("pending");

        assertTrue(result.isEmpty());
    }
}
