package com.hms.finance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LineItemRequest {

    @NotBlank(message = "item_name is required")
    private String itemName;

    @NotBlank(message = "item_type is required")
    private String itemType; // consultation, procedure, medicine, lab_test, bed_charge, wound_care, other

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "unit_price is required")
    @Positive(message = "unit_price must be positive")
    private BigDecimal unitPrice;
}
