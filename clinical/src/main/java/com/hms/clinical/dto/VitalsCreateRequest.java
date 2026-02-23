package com.hms.clinical.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VitalsCreateRequest {

    @Min(20)
    @Max(300)
    private Integer bpm;

    @Min(30)
    @Max(45)
    private Double temperature;

    private Integer bloodPressureSys;

    private Integer bloodPressureDia;

    @Min(0)
    @Max(100)
    private Integer spo2;

    private Double weightKg;

    private Integer heightCm;

    private Double bloodSugar;
}
