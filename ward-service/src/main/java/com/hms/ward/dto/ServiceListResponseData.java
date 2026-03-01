package com.hms.ward.dto;

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
public class ServiceListResponseData {
    private UUID admissionId;
    private String patientName;
    private ZonedDateTime admittedAt;
    private long daysAdmitted;
    private List<WardServiceDto> services;
    private BigDecimal runningTotal;
}
