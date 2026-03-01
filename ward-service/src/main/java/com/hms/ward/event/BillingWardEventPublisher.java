package com.hms.ward.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.ward.config.NatsConfig;
import com.hms.ward.entity.Admission;
import com.hms.ward.entity.Bed;
import com.hms.ward.entity.Ward;
import com.hms.ward.entity.WardServiceCharge;
import io.nats.client.JetStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingWardEventPublisher {

    private final JetStream jetStream;
    private final ObjectMapper objectMapper;

    public void publishBillingWard(Admission admission, Ward ward, Bed bed,
                                    List<WardServiceCharge> services, BigDecimal total) {
        try {
            List<BillingWardEvent.WardServiceItem> serviceItems = services.stream()
                    .map(s -> new BillingWardEvent.WardServiceItem(
                            s.getServiceName(),
                            s.getServiceType(),
                            s.getQuantity(),
                            s.getUnitPrice() != null ? s.getUnitPrice().doubleValue() : 0.0
                    ))
                    .toList();

            BillingWardEvent event = BillingWardEvent.builder()
                    .admissionId(admission.getId())
                    .patientId(admission.getPatientId())
                    .patientName(admission.getPatientName())
                    .services(serviceItems)
                    .totalAmount(total != null ? total.doubleValue() : 0.0)
                    .dischargedAt(admission.getDischargedAt() != null
                            ? admission.getDischargedAt().toLocalDateTime()
                            : null)
                    .build();

            byte[] payload = objectMapper.writeValueAsBytes(event);
            jetStream.publish(NatsConfig.SUBJECT_BILLING_WARD, payload);
            log.info("Published billing.ward event for admission {}", admission.getId());
        } catch (Exception e) {
            log.error("Failed to publish billing.ward event for admission {}: {}",
                    admission.getId(), e.getMessage(), e);
        }
    }
}
