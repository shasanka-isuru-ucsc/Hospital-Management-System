package com.hms.lab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTestDto {
    private UUID id;
    private UUID orderId;
    private UUID testId;
    private String testName;
    private String testCode;
    private String urgency;
    private BigDecimal unitPrice;
    private String resultValue;
    private Boolean isAbnormal;
    private String technicianNotes;
    private String status;
}
