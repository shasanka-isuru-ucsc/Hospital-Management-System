package com.hms.clinical.dto;

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
public class LabRequestDto {
    private UUID id;
    private UUID sessionId;
    private UUID testId;
    private String testName;
    private String urgency;
    private UUID labOrderId;
    private String orderStatus;
    private LocalDateTime requestedAt;
}
