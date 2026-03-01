package com.hms.lab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabOrderDto {
    private UUID id;
    private UUID patientId;
    private String patientName;
    private String patientMobile;
    private UUID sessionId;
    private String source;
    private List<OrderTestDto> tests;
    private BigDecimal totalAmount;
    private String status;
    private String paymentStatus;
    private String reportFileUrl; // Pre-signed MinIO URL (15-min TTL), generated on demand
    private String reportNotes;
    private String createdBy;
    private ZonedDateTime createdAt;
    private ZonedDateTime completedAt;
}
