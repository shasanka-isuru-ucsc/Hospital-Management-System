package com.hms.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {

    @NotBlank(message = "payment_method is required")
    private String paymentMethod; // cash, card, online, insurance

    @NotNull(message = "amount_paid is required")
    @Positive(message = "amount_paid must be positive")
    private BigDecimal amountPaid;

    private String notes;
}
