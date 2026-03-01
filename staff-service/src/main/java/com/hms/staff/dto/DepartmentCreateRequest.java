package com.hms.staff.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class DepartmentCreateRequest {

    @NotBlank(message = "name is required")
    private String name;

    private String description;
    private UUID headDoctorId;
}
