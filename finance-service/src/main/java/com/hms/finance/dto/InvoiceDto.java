package com.hms.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDto {
    private UUID id;
    private String invoiceNumber;
    private UUID patientId;
    private String patientName;
    private String billingModule;
    private UUID sessionReferenceId;
    private List<LineItemDto> lineItems;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private String discountReason;
    private BigDecimal totalAmount;
    private String paymentStatus;
    private String paymentMethod;
    private String source;
    private ZonedDateTime paidAt;
    private String createdBy;
    private ZonedDateTime createdAt;
}
