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
public class LabTestDto {
    private UUID id;
    private String name;
    private String code;
    private String category;
    private BigDecimal unitPrice;
    private Integer turnaroundHours;
    private String referenceRange;
    private Boolean isActive;
}
