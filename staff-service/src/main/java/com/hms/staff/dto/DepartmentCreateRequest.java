package com.hms.staff.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DepartmentCreateRequest {

    @NotBlank(message = "name is required")
    private String name;

    private String description;
    private UUID headDoctorId;
}
