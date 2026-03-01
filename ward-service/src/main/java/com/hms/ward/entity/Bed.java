package com.hms.ward.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "beds", schema = "ward")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bed {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ward_id", nullable = false)
    private UUID wardId;

    @Column(name = "bed_number", nullable = false, length = 20)
    private String bedNumber; // e.g. B-101

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "available"; // available | occupied | maintenance | reserved

    @Column(name = "current_admission_id")
    private UUID currentAdmissionId; // Set when occupied

    @Column(name = "current_patient_name", length = 200)
    private String currentPatientName; // Denormalized for quick display

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
