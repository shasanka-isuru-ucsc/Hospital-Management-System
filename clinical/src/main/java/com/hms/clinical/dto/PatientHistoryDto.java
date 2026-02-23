package com.hms.clinical.dto;

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
public class PatientHistoryDto {

    private PatientInfo patient;
    private List<SessionDto> sessions;
    private VitalsSummary vitalsSummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatientInfo {
        private UUID id;
        private String fullName;
        private String patientNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VitalsSummary {
        private Integer latestBpm;
        private Double latestTemperature;
        private String latestBloodPressure;
        private Double latestWeightKg;
    }
}
