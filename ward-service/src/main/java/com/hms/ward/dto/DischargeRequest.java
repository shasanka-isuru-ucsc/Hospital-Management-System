package com.hms.ward.dto;

import lombok.Data;

@Data
public class DischargeRequest {
    private String dischargeNotes;
    private String dischargeDiagnosis;
}
