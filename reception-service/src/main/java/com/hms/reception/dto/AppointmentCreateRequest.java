package com.hms.reception.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class AppointmentCreateRequest {

    private UUID patientId;
    private String patientName;
    private String patientMobile;
    private String gender;
    private String address;

    @NotNull(message = "doctorId is required")
    private UUID doctorId;

    @NotNull(message = "appointmentDate is required")
    private LocalDate appointmentDate;

    @NotNull(message = "fromTime is required")
    private LocalTime fromTime;

    @NotNull(message = "toTime is required")
    private LocalTime toTime;

    private String treatment;
    private String notes;
}
