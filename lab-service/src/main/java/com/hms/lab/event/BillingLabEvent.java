package com.hms.lab.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

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
    private String paymentMethod;
    private ZonedDateTime paidAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabTestItem {
        private String testName;
        private Double price;
    }
}
