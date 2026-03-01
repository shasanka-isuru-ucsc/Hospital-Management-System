package com.hms.lab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class OrderTestRequest {

    @NotNull(message = "Test ID is required")
    private UUID testId;

    private String urgency = "routine"; // routine | urgent
}
