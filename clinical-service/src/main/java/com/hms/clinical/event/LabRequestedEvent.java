package com.hms.clinical.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabRequestedEvent {
    private UUID sessionId;
    private UUID patientId;
    private String patientName;
    private UUID doctorId;
    private String doctorName;
    private String notes;
    private List<TestItem> tests;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestItem {
        private UUID requestId;
        private UUID testId;
        private String testName;
        private String urgency;
        private LocalDateTime requestedAt;
    }
}
