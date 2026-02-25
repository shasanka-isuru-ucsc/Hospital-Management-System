package com.hms.staff.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private LocalDateTime allocatedAt;
    private String allocatedBy;
}
