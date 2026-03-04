package com.hms.clinical.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionCompleteRequest {

    @NotBlank(message = "diagnosis is required")
    private String diagnosis;

    private Double consultationFee;

    private Double discountPercent;

    private String discountReason;
}
