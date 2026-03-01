package com.hms.ward.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Published to billing.events exchange with routing key billing.ward on patient discharge.
 * Finance Service consumes this to create the ward admission invoice.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingWardEvent {
    private UUID admissionId;
    private UUID patientId;
    private String patientName;
    private List<WardServiceItem> services;
    private Double totalAmount;
    private LocalDateTime dischargedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WardServiceItem {
        private String name;
        private String type; // bed_charge | procedure | medication | other
        private Integer quantity;
        private Double unitPrice;
    }
}
