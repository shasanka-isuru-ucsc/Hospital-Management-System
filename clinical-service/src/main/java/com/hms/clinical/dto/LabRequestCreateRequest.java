package com.hms.clinical.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabRequestCreateRequest {

    @NotEmpty(message = "tests list is required")
    @Valid
    private List<TestItem> tests;

    private String notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestItem {
        @NotNull(message = "test_id is required")
        private UUID testId;

        private String urgency; // routine (default), urgent
    }
}
