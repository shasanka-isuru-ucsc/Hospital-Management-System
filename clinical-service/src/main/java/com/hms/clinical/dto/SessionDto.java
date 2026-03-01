package com.hms.clinical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionDto {
    private UUID id;
    private UUID patientId;
    private String patientName;
    private UUID doctorId;
    private String doctorName;
    private UUID nurseId;
    private String sessionType;
    private UUID appointmentId;
    private String chiefComplaint;
    private String diagnosis;
    private LocalDate followUpDate;
    private String status;
    private Double discountPercent;
    private String discountReason;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private VitalsDto vitals;
    private List<PrescriptionDto> prescriptions;
    private List<SessionImageDto> images;
    private List<LabRequestDto> labRequests;
}
