package com.hms.ward.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WardServiceDto {
    private UUID id;
    private UUID admissionId;
    private String serviceName;
    private String serviceType;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private ZonedDateTime providedAt;
    private String addedBy;
    private String notes;
}
