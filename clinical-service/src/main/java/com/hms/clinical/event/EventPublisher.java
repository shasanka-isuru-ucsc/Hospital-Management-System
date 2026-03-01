package com.hms.clinical.event;

import com.hms.clinical.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishBillingOpdEvent(BillingEvent event) {
        log.info("Publishing billing.opd event for session: {}", event.getSessionId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.BILLING_EXCHANGE,
                RabbitMQConfig.BILLING_OPD_ROUTING_KEY,
                event);
    }

    public void publishBillingWoundEvent(BillingEvent event) {
        log.info("Publishing billing.wound event for session: {}", event.getSessionId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.BILLING_EXCHANGE,
                RabbitMQConfig.BILLING_WOUND_ROUTING_KEY,
                event);
    }

    public void publishLabRequestedEvent(LabRequestedEvent event) {
        log.info("Publishing lab.requested event for session: {}", event.getSessionId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.LAB_EXCHANGE,
                RabbitMQConfig.LAB_REQUESTED_ROUTING_KEY,
                event);
    }
}
