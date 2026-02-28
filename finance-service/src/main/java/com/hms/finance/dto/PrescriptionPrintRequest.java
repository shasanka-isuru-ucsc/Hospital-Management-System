package com.hms.finance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class PrescriptionPrintRequest {

    @NotNull(message = "session_id is required")
    private UUID sessionId;

    private String doctorName;

    private String hospitalName;
}
