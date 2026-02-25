package com.hms.staff.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentDto {
    private UUID id;
    private String name;
    private String description;
    private UUID headDoctorId;
    private String headDoctorName;
    private boolean isActive;
    private long doctorCount;
}
