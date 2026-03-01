package com.hms.reception.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.reception.config.NatsConfig;
import io.nats.client.JetStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueEventPublisher {

    private final JetStream jetStream;
    private final ObjectMapper objectMapper;

    public void publishQueueUpdated(QueueUpdatedEvent event) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            jetStream.publish(NatsConfig.SUBJECT_QUEUE_UPDATED, payload);
            log.info("Published queue.updated event for token {}", event.getTokenNumber());
        } catch (Exception e) {
            log.error("Failed to publish queue.updated event for token {}: {}",
                    event.getTokenNumber(), e.getMessage(), e);
        }
    }
}
