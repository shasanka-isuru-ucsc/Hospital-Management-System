package com.hms.staff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class DoctorCreateRequest {

    @NotBlank(message = "first_name is required")
    private String firstName;

    @NotBlank(message = "last_name is required")
    private String lastName;

    @NotBlank(message = "username is required")
    private String username;

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    private String email;

    @NotBlank(message = "mobile is required")
    private String mobile;

    @NotBlank(message = "password is required")
    private String password;

    @NotNull(message = "date_of_birth is required")
    private LocalDate dateOfBirth;

    @NotBlank(message = "gender is required")
    private String gender;

    @NotBlank(message = "education is required")
    private String education;

    @NotBlank(message = "designation is required")
    private String designation;

    @NotNull(message = "department_id is required")
    private UUID departmentId;

    private String address;
    private String city;
    private String country;
    private String stateProvince;
    private String postalCode;
    private String biography;
    private String status = "active";
}
