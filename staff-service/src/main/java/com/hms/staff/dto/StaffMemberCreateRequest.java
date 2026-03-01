package com.hms.staff.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
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
