package com.hms.clinical.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionCreateRequest {

    @NotEmpty(message = "prescriptions list is required")
    @Valid
    private List<PrescriptionItem> prescriptions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrescriptionItem {
        @NotBlank(message = "type is required")
        private String type; // internal, external

        @NotBlank(message = "medicine_name is required")
        private String medicineName;

        @NotBlank(message = "dosage is required")
        private String dosage;

        @NotBlank(message = "frequency is required")
        private String frequency;

        @NotNull(message = "duration_days is required")
        private Integer durationDays;

        private String instructions;

        private String pharmacyName;
    }
}
