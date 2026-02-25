package com.hms.staff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorCreateRequest {
    @NotBlank(message = "First name is required")
    private String firstName;
    @NotBlank(message = "Last name is required")
    private String lastName;
    @NotBlank(message = "Username is required")
    private String username;
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    @NotBlank(message = "Mobile is required")
    private String mobile;
    @NotBlank(message = "Password is required")
    private String password;
    @NotNull(message = "Date of birth is required")
    private LocalDate dateOfBirth;
    @NotBlank(message = "Gender is required")
    private String gender;
    @NotBlank(message = "Education is required")
    private String education;
    @NotBlank(message = "Designation is required")
    private String designation;
    @NotNull(message = "Department ID is required")
    private UUID departmentId;
    private String address;
    private String city;
    private String country;
    private String stateProvince;
    private String postalCode;
    private String biography;
    private String status;
}
