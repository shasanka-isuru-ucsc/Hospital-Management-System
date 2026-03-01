package com.hms.ward.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AdmissionCreateRequest {

    @NotNull(message = "Patient ID is required")
    private UUID patientId;

    private String patientName;   // Denormalized from reception-service (sent by frontend)
    private String patientNumber; // e.g. R00001

    @NotNull(message = "Bed ID is required")
    private UUID bedId;

    @NotNull(message = "Attending doctor ID is required")
    private UUID attendingDoctorId;

    private String attendingDoctorName; // Denormalized from staff-service (sent by frontend)

    @NotBlank(message = "Admission reason is required")
    private String admissionReason;

    private String notes;
}
