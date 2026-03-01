package com.hms.lab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ResultItem {

    @NotNull(message = "Order test ID is required")
    private UUID orderTestId;

    @NotBlank(message = "Result value is required")
    private String resultValue;

    private Boolean isAbnormal;

    private String technicianNotes;
}
