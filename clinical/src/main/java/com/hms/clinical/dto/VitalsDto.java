package com.hms.clinical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VitalsDto {
    private UUID id;
    private UUID sessionId;
    private Integer bpm;
    private Double temperature;
    private Integer bloodPressureSys;
    private Integer bloodPressureDia;
    private Integer spo2;
    private Double weightKg;
    private Integer heightCm;
    private Double bloodSugar;
    private LocalDateTime recordedAt;
    private LocalDate sessionDate;
}
