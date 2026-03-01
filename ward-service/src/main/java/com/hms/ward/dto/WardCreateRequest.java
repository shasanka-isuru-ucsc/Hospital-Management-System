package com.hms.ward.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WardCreateRequest {

    @NotBlank(message = "Ward name is required")
    private String name;

    @NotBlank(message = "Ward type is required")
    private String type; // general | icu | private | semi_private

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;
}
