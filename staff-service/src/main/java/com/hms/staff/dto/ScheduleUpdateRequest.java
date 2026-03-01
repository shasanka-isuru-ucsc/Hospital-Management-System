package com.hms.staff.dto;

import lombok.Data;

@Data
public class ScheduleUpdateRequest {
    private String startTime;
    private String endTime;
    private Integer slotDurationMinutes;
    private Integer maxPatients;
    private Boolean isActive;
}
