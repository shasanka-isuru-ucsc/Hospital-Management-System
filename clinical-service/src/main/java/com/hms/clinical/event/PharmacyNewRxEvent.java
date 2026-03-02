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
public class PharmacyNewRxEvent {
    private UUID sessionId;
    private UUID patientId;
    private String patientName;
    private String doctorName;
    private LocalDateTime completedAt;
    private List<RxItem> prescriptions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RxItem {
        private UUID id;
        private String medicineName;
        private String dosage;
        private String frequency;
        private Integer durationDays;
        private String instructions;
    }
}
