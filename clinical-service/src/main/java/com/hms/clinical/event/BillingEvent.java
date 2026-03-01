package com.hms.clinical.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingEvent {
    private UUID sessionId;
    private UUID patientId;
    private String patientName;
    private String sessionType;
    private UUID doctorId;
    private String doctorName;
    private Double discountPercent;
    private String discountReason;
    private LocalDateTime completedAt;
}
