package com.hms.staff.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class DoctorUpdateRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String mobile;
    private String education;
    private String designation;
    private UUID departmentId;
    private String address;
    private String city;
    private String country;
    private String stateProvince;
    private String postalCode;
    private String biography;
    private String status;
}
