package com.hms.lab.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LabTestCreateRequest {

    @NotBlank(message = "Test name is required")
    private String name;

    @NotBlank(message = "Test code is required")
    private String code;

    private String category;

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.01", message = "Unit price must be greater than 0")
    private BigDecimal unitPrice;

    private Integer turnaroundHours;

    private String referenceRange;
}
