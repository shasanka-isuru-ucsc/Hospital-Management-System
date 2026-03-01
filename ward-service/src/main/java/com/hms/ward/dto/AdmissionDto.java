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
public class AdmissionDto {
    private UUID id;
    private UUID patientId;
    private String patientName;
    private String patientNumber;
    private UUID bedId;
    private String bedNumber;
    private UUID wardId;
    private String wardName;
    private UUID attendingDoctorId;
    private String attendingDoctorName;
    private String admissionReason;
    private String notes;
    private String status;
    private ZonedDateTime admittedAt;
    private ZonedDateTime dischargedAt;
    private String dischargeNotes;
    private String dischargeDiagnosis;
    private List<WardServiceDto> services;
    private BigDecimal runningTotal;
    private long daysAdmitted;
}
