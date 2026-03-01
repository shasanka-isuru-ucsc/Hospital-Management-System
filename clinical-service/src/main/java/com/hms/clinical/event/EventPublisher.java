package com.hms.clinical.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.clinical.config.NatsConfig;
import io.nats.client.JetStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final JetStream jetStream;
    private final ObjectMapper objectMapper;

    public void publishBillingOpdEvent(BillingEvent event) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            jetStream.publish(NatsConfig.SUBJECT_BILLING_OPD, payload);
            log.info("Published billing.opd event for session: {}", event.getSessionId());
        } catch (Exception e) {
            log.error("Failed to publish billing.opd event for session {}: {}",
                    event.getSessionId(), e.getMessage(), e);
        }
    }

    public void publishBillingWoundEvent(BillingEvent event) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            jetStream.publish(NatsConfig.SUBJECT_BILLING_WOUND, payload);
            log.info("Published billing.wound event for session: {}", event.getSessionId());
        } catch (Exception e) {
            log.error("Failed to publish billing.wound event for session {}: {}",
                    event.getSessionId(), e.getMessage(), e);
        }
    }

    public void publishLabRequestedEvent(LabRequestedEvent event) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            jetStream.publish(NatsConfig.SUBJECT_LAB_REQUESTED, payload);
            log.info("Published lab.requested event for session: {}", event.getSessionId());
        } catch (Exception e) {
            log.error("Failed to publish lab.requested event for session {}: {}",
                    event.getSessionId(), e.getMessage(), e);
        }
    }
}
