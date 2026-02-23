package com.hms.reception.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class TokenCreateRequest {
    @NotNull(message = "patientId is required")
    private UUID patientId;

    @NotNull(message = "queueType is required")
    private String queueType;

    private UUID doctorId;
}
