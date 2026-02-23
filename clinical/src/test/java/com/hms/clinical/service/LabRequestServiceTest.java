package com.hms.clinical.service;

import com.hms.clinical.dto.LabRequestCreateRequest;
import com.hms.clinical.dto.LabRequestDto;
import com.hms.clinical.entity.LabRequest;
import com.hms.clinical.entity.Session;
import com.hms.clinical.event.EventPublisher;
import com.hms.clinical.event.LabRequestedEvent;
import com.hms.clinical.exception.BusinessException;
import com.hms.clinical.repository.LabRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabRequestServiceTest {

    @Mock
    private LabRequestRepository labRequestRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private LabRequestService labRequestService;

    @Test
    void createLabRequests_ShouldCreateAndPublishEvent() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setPatientId(UUID.randomUUID());
        session.setPatientName("Patient");
        session.setDoctorId(UUID.randomUUID());
        session.setDoctorName("Dr. Smith");
        session.setStatus("open");

        when(sessionService.getSessionEntity(sessionId)).thenReturn(session);
        when(labRequestRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<LabRequest> list = inv.getArgument(0);
            list.forEach(lr -> lr.setId(UUID.randomUUID()));
            return list;
        });

        LabRequestCreateRequest request = LabRequestCreateRequest.builder()
                .tests(List.of(
                        LabRequestCreateRequest.TestItem.builder()
                                .testId(UUID.randomUUID())
                                .urgency("routine")
                                .build(),
                        LabRequestCreateRequest.TestItem.builder()
                                .testId(UUID.randomUUID())
                                .urgency("urgent")
                                .build()))
                .notes("Check for typhoid")
                .build();

        List<LabRequestDto> result = labRequestService.createLabRequests(sessionId, request);

        assertEquals(2, result.size());
        ArgumentCaptor<LabRequestedEvent> captor = ArgumentCaptor.forClass(LabRequestedEvent.class);
        verify(eventPublisher, times(1)).publishLabRequestedEvent(captor.capture());
        assertEquals(sessionId, captor.getValue().getSessionId());
        assertEquals(2, captor.getValue().getTests().size());
    }

    @Test
    void createLabRequests_CompletedSession_ShouldThrow() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setStatus("completed");

        when(sessionService.getSessionEntity(sessionId)).thenReturn(session);

        LabRequestCreateRequest request = LabRequestCreateRequest.builder()
                .tests(List.of(
                        LabRequestCreateRequest.TestItem.builder()
                                .testId(UUID.randomUUID())
                                .build()))
                .build();

        assertThrows(BusinessException.class, () -> labRequestService.createLabRequests(sessionId, request));
    }

    @Test
    void getLabRequests_ShouldReturnList() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);

        LabRequest lr = new LabRequest();
        lr.setId(UUID.randomUUID());
        lr.setSession(session);
        lr.setTestId(UUID.randomUUID());
        lr.setTestName("CBC");
        lr.setUrgency("routine");

        when(labRequestRepository.findBySessionId(sessionId)).thenReturn(List.of(lr));

        List<LabRequestDto> result = labRequestService.getLabRequests(sessionId);
        assertEquals(1, result.size());
        assertEquals("CBC", result.get(0).getTestName());
    }
}
