package com.hms.lab.event;

import com.hms.lab.config.RabbitMQConfig;
import com.hms.lab.entity.LabOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingLabEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishBillingLab(LabOrder order) {
        try {
            List<BillingLabEvent.LabTestItem> testItems = order.getTests().stream()
                    .map(t -> BillingLabEvent.LabTestItem.builder()
                            .testName(t.getTestName())
                            .price(t.getUnitPrice() != null ? t.getUnitPrice().doubleValue() : 0.0)
                            .build())
                    .toList();

            BillingLabEvent event = BillingLabEvent.builder()
                    .orderId(order.getId())
                    .patientId(order.getPatientId())
                    .patientName(order.getPatientName())
                    .tests(testItems)
                    .totalAmount(order.getTotalAmount() != null ? order.getTotalAmount().doubleValue() : 0.0)
                    .paymentMethod(order.getPaymentMethod())
                    .paidAt(order.getPaidAt())
                    .build();

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.BILLING_EXCHANGE,
                    RabbitMQConfig.BILLING_LAB_ROUTING_KEY,
                    event);

            log.info("Published billing.lab event for order {}", order.getId());
        } catch (Exception e) {
            log.error("Failed to publish billing.lab event for order {}: {}", order.getId(), e.getMessage(), e);
        }
    }
}
