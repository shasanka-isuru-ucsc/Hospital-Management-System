package com.hms.lab.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.lab.config.NatsConfig;
import com.hms.lab.dto.LabOrderCreateRequest;
import com.hms.lab.dto.LabOrderDto;
import com.hms.lab.dto.OrderTestRequest;
import com.hms.lab.exception.BusinessException;
import com.hms.lab.service.LabOrderService;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LabRequestedEventConsumer {

    private final Connection natsConnection;
    private final JetStream jetStream;
    private final LabOrderService labOrderService;
    private final ObjectMapper objectMapper;

    private Dispatcher dispatcher;

    @PostConstruct
    public void subscribe() {
        try {
            dispatcher = natsConnection.createDispatcher();

            jetStream.subscribe(NatsConfig.SUBJECT_LAB_REQUESTED, dispatcher,
                    this::handleMessage, false,
                    PushSubscribeOptions.builder().durable("lab-requested").build());

            log.info("Lab service subscribed to lab.requested NATS subject");
        } catch (Exception e) {
            log.error("Failed to subscribe to NATS lab.requested subject", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (dispatcher != null) {
            natsConnection.closeDispatcher(dispatcher);
        }
    }

    private void handleMessage(Message msg) {
        try {
            LabRequestedEvent event = objectMapper.readValue(msg.getData(), LabRequestedEvent.class);
            handleLabRequested(event);
            msg.ack();
        } catch (Exception e) {
            log.error("Error processing NATS message on subject lab.requested", e);
        }
    }

    private void handleLabRequested(LabRequestedEvent event) {
        log.info("Received lab.requested event for session: {}, patient: {}",
                event.getSessionId(), event.getPatientName());
        try {
            List<OrderTestRequest> testRequests = event.getTests().stream()
                    .map(t -> {
                        OrderTestRequest req = new OrderTestRequest();
                        req.setTestId(t.getTestId());
                        req.setUrgency(t.getUrgency() != null ? t.getUrgency() : "routine");
                        return req;
                    })
                    .toList();

            LabOrderCreateRequest request = new LabOrderCreateRequest();
            request.setPatientId(event.getPatientId());
            request.setPatientName(event.getPatientName());
            request.setSessionId(event.getSessionId());
            request.setSource("clinical_request");
            request.setTests(testRequests);
            request.setNotes(event.getNotes());

            LabOrderDto order = labOrderService.createOrder(request, "system");
            log.info("Auto-created lab order {} from clinical session {}", order.getId(), event.getSessionId());

        } catch (BusinessException e) {
            if ("DUPLICATE_ORDER".equals(e.getCode())) {
                log.warn("Duplicate lab order for session {}, ignoring.", event.getSessionId());
            } else {
                log.error("Error processing lab.requested event for session {}: {}",
                        event.getSessionId(), e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected error processing lab.requested event for session {}",
                    event.getSessionId(), e);
        }
    }
}
