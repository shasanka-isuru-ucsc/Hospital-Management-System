package com.hms.staff.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class DepartmentUpdateRequest {
    private String name;
    private String description;
    private UUID headDoctorId;
    private Boolean isActive;
}
