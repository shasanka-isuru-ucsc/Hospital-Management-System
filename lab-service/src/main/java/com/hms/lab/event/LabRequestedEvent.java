package com.hms.lab.event;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
public class LabRequestedEvent {

    private UUID sessionId;
    private UUID patientId;
    private String patientName;
    private UUID doctorId;
    private List<LabTestItem> tests;
    private String notes;

    @Data
    @NoArgsConstructor
    public static class LabTestItem {
        private UUID testId;
        private String urgency; // routine | urgent
    }
}
