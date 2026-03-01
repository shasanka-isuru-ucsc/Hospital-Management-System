package com.hms.staff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDto {
    private UUID id;
    private UUID doctorId;
    private Integer dayOfWeek;
    private String dayName;
    private String startTime;
    private String endTime;
    private Integer slotDurationMinutes;
    private Integer maxPatients;
    private Boolean isActive;
}
