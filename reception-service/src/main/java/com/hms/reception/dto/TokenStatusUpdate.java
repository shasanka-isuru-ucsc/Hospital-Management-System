package com.hms.reception.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenStatusUpdate {
    @NotBlank(message = "status is required")
    private String status;
}
