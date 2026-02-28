package com.hms.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResultDto {
    private InvoiceDto invoice;
    private BigDecimal changeAmount;
}
