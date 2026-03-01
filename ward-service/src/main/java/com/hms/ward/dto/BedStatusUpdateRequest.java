package com.hms.ward.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BedStatusUpdateRequest {

    @NotBlank(message = "Status is required")
    private String status; // available | maintenance | reserved (not occupied — set via admissions)

    private String notes;
}
