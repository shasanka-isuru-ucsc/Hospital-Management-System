package com.hms.staff.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class NurseAllocationCreateRequest {

    @NotNull(message = "nurse_id is required")
    private UUID nurseId;

    @NotNull(message = "doctor_id is required")
    private UUID doctorId;

    @NotNull(message = "session_date is required")
    private LocalDate sessionDate;
}
