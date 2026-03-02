package com.hms.staff.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StaffMemberCreateRequest {

    @NotBlank(message = "full_name is required")
    private String fullName;

    @NotBlank(message = "role is required")
    private String role;

    private String email;
    private String mobile;
    private String status = "active";
    private String keycloakUserId;
}
