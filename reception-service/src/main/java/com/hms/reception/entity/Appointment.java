package com.hms.reception.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Appointment extends BaseEntity {

    @ManyToOne(optional = true)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "from_time", nullable = false)
    private LocalTime fromTime;

    @Column(name = "to_time", nullable = false)
    private LocalTime toTime;

    @Column(name = "treatment", length = 200)
    private String treatment;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "booked_by")
    private UUID bookedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    // For walk-in patients (unregistered)
    @Column(name = "patient_name")
    private String patientName;

    @Column(name = "patient_mobile")
    private String patientMobile;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

}
