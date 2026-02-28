package com.hms.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class InvoiceCreateRequest {

    @NotNull(message = "patient_id is required")
    private UUID patientId;

    @NotBlank(message = "patient_name is required")
    private String patientName;

    @NotBlank(message = "billing_module is required")
    private String billingModule; // opd, wound_care, channelling, lab, ward, pharmacy

    private UUID sessionReferenceId;

    @NotEmpty(message = "line_items must not be empty")
    @Valid
    private List<LineItemRequest> lineItems;

    private String notes;
}
