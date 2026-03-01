package com.hms.clinical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionUpdateRequest {
    private String chiefComplaint;
    private String diagnosis;
    private LocalDate followUpDate;
    private String notes;
}
