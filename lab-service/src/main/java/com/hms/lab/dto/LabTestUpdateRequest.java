package com.hms.lab.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LabTestUpdateRequest {

    private String name;

    private String category;

    @DecimalMin(value = "0.01", message = "Unit price must be greater than 0")
    private BigDecimal unitPrice;

    private Integer turnaroundHours;

    private String referenceRange;

    private Boolean isActive;
}
