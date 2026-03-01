package com.hms.ward.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BedDto {
    private UUID id;
    private UUID wardId;
    private String wardName;
    private String bedNumber;
    private String status;
    private UUID currentAdmissionId;
    private String currentPatientName;
}
