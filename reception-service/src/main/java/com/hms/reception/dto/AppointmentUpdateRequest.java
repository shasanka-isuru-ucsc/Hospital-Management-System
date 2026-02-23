package com.hms.reception.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class AppointmentUpdateRequest {
    private UUID doctorId;
    private LocalDate appointmentDate;
    private String fromTime;
    private String toTime;
    private String treatment;
    private String notes;
    private String status;
}
