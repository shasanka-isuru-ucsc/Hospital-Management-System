package com.hms.finance.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Received from Clinical Service when an OPD session is completed.
 * routing key: billing.opd
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingOpdEvent {
    private UUID sessionId;
    private UUID patientId;
    private String patientName;
    private UUID doctorId;
    private String doctorName;
    private Double consultationFee;
    private Double discountPercent;
    private String discountReason;
    private LocalDateTime completedAt;
}
