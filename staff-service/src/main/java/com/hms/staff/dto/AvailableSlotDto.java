package com.hms.staff.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableSlotDto {
    private String fromTime;
    private String toTime;
    private boolean isAvailable;
    private UUID appointmentId;
}
