package com.hms.lab.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class LabOrderCreateRequest {

    private UUID patientId;

    private String patientName;

    private String patientMobile;

    private UUID sessionId;

    private String source = "walk_in"; // walk_in | clinical_request

    @NotEmpty(message = "At least one test is required")
    @Valid
    private List<OrderTestRequest> tests;

    private String notes;
}
