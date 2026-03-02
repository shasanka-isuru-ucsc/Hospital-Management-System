package com.hms.staff.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class NurseAllocationCreateRequest {

    @NotNull(message = "nurse_id is required")
    private UUID nurseId;

    @NotNull(message = "doctor_id is required")
    private UUID doctorId;

    @NotNull(message = "session_date is required")
    private LocalDate sessionDate;
}
