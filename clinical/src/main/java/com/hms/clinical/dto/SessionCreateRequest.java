package com.hms.clinical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionCreateRequest {

    @NotNull(message = "patient_id is required")
    private UUID patientId;

    @NotBlank(message = "session_type is required")
    private String sessionType;

    private UUID appointmentId;

    private String chiefComplaint;
}
