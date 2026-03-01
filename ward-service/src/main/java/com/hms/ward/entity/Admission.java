package com.hms.ward.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "admissions", schema = "ward")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Admission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "patient_name", nullable = false, length = 200)
    private String patientName; // Denormalized

    @Column(name = "patient_number", length = 20)
    private String patientNumber; // e.g. R00001 — denormalized

    @Column(name = "bed_id", nullable = false)
    private UUID bedId;

    @Column(name = "ward_id", nullable = false)
    private UUID wardId;

    @Column(name = "attending_doctor_id")
    private UUID attendingDoctorId;

    @Column(name = "attending_doctor_name", length = 200)
    private String attendingDoctorName; // Denormalized

    @Column(name = "admission_reason", nullable = false, columnDefinition = "TEXT")
    private String admissionReason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "admitted"; // admitted | discharged

    @CreationTimestamp
    @Column(name = "admitted_at", nullable = false, updatable = false)
    private ZonedDateTime admittedAt;

    @Column(name = "discharged_at")
    private ZonedDateTime dischargedAt;

    @Column(name = "discharge_notes", columnDefinition = "TEXT")
    private String dischargeNotes;

    @Column(name = "discharge_diagnosis", columnDefinition = "TEXT")
    private String dischargeDiagnosis;
}
