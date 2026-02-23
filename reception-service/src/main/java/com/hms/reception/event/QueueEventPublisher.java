package com.hms.reception.event;

import com.hms.reception.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishQueueUpdated(QueueUpdatedEvent event) {
        log.info("Publishing queue.updated event for token {}", event.getTokenNumber());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_QUEUE_EVENTS, "queue.updated", event);
    }
}
