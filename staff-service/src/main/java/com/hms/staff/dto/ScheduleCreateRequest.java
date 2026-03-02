package com.hms.staff.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ScheduleCreateRequest {

    @NotNull(message = "day_of_week is required")
    @Min(value = 0, message = "day_of_week must be between 0 and 6")
    @Max(value = 6, message = "day_of_week must be between 0 and 6")
    private Integer dayOfWeek;

    @NotNull(message = "start_time is required")
    private String startTime;

    @NotNull(message = "end_time is required")
    private String endTime;

    @NotNull(message = "slot_duration_minutes is required")
    @Min(value = 5, message = "slot_duration_minutes must be at least 5")
    private Integer slotDurationMinutes;

    @NotNull(message = "max_patients is required")
    @Min(value = 1, message = "max_patients must be at least 1")
    private Integer maxPatients;
}
