package com.hms.clinical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionDto {
    private UUID id;
    private UUID sessionId;
    private String type;
    private String medicineName;
    private String dosage;
    private String frequency;
    private Integer durationDays;
    private String instructions;
    private String pharmacyName;
    private String status;
    private LocalDateTime createdAt;
    // Extra fields for pharmacy queue
    private String patientName;
    private LocalDate sessionDate;
    private String doctorName;
}
