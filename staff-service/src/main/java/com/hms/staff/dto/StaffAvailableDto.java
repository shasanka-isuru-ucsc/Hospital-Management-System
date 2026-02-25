package com.hms.staff.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffAvailableDto {
    private UUID id;
    private String fullName;
    private String role;
    private String mobile;
}
