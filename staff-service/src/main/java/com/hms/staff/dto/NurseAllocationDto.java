package com.hms.staff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NurseAllocationDto {
    private UUID id;
    private UUID nurseId;
    private String nurseName;
    private UUID doctorId;
    private String doctorName;
    private LocalDate sessionDate;
    private String status;
    private ZonedDateTime allocatedAt;
    private String allocatedBy;
}
