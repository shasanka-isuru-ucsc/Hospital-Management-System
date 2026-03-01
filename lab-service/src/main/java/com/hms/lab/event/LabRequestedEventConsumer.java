package com.hms.lab.event;

import com.hms.lab.config.RabbitMQConfig;
import com.hms.lab.dto.LabOrderCreateRequest;
import com.hms.lab.dto.LabOrderDto;
import com.hms.lab.dto.OrderTestRequest;
import com.hms.lab.exception.BusinessException;
import com.hms.lab.service.LabOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LabRequestedEventConsumer {

    private final LabOrderService labOrderService;

    @RabbitListener(queues = RabbitMQConfig.LAB_REQUESTED_QUEUE)
    public void handleLabRequested(LabRequestedEvent event) {
        log.info("Received lab.requested event for session: {}, patient: {}",
                event.getSessionId(), event.getPatientName());
        try {
            // Map event tests to request format
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
