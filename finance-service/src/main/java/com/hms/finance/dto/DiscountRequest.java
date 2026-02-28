package com.hms.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DiscountRequest {

    @NotBlank(message = "discount_type is required")
    private String discountType; // percentage | flat

    @NotNull(message = "discount_value is required")
    @Positive(message = "discount_value must be positive")
    private BigDecimal discountValue;

    @NotBlank(message = "reason is required")
    private String reason;
}
