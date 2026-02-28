package com.hms.finance.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Received from Lab Service when lab tests are ordered/paid.
 * routing key: billing.lab
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingLabEvent {
    private UUID orderId;
    private UUID patientId;
    private String patientName;
    private List<LabTestItem> tests;
    private Double totalAmount;
    private LocalDateTime paidAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabTestItem {
        private String testName;
        private Double price;
    }
}
