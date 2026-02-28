package com.hms.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineItemDto {
    private UUID id;
    private UUID invoiceId;
    private String itemName;
    private String itemType;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}
