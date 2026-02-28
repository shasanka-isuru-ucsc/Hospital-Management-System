package com.hms.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintDataDto {
    private String invoiceNumber;
    private String hospitalName;
    private String patientName;
    private String patientNumber;
    private String date;
    private String time;
    private List<LineItemDto> lineItems;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private ZonedDateTime paidAt;
    private String cashierName;
}
