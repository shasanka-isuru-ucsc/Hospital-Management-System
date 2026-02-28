package com.hms.finance.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Received from Clinical Service when a wound care session is completed.
 * routing key: billing.wound
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingWoundEvent {
    private UUID sessionId;
    private UUID patientId;
    private String patientName;
    private Double woundCareFee;
    private Double discountPercent;
    private LocalDateTime completedAt;
}
