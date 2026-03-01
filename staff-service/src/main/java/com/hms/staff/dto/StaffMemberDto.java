package com.hms.staff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffMemberDto {
    private UUID id;
    private String fullName;
    private String role;
    private String email;
    private String mobile;
    private String status;
}
