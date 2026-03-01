package com.hms.lab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportUploadResponse {
    private UUID orderId;
    private String reportUrl;
    private ZonedDateTime expiresAt;
    private String status;
}
