package com.hms.staff.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorDto {
    private UUID id;
    private String keycloakUserId;
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private String mobile;
    private LocalDate dateOfBirth;
    private String gender;
    private String education;
    private String designation;
    private UUID departmentId;
    private String departmentName;
    private String address;
    private String city;
    private String country;
    private String stateProvince;
    private String postalCode;
    private String biography;
    private String avatarUrl;
    private String bannerUrl;
    private String status;
    private LocalDate joiningDate;
    private LocalDateTime createdAt;
}
