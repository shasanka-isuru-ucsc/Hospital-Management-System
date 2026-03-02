package com.hms.staff.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ScheduleUpdateRequest {
    private String startTime;
    private String endTime;
    private Integer slotDurationMinutes;
    private Integer maxPatients;
    private Boolean isActive;
}
