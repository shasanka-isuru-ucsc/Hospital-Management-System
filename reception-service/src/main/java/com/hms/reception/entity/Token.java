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
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Token extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "token_number", nullable = false)
    private Integer tokenNumber;

    @Column(name = "queue_type", nullable = false, length = 30)
    private String queueType;

    @Column(name = "doctor_id")
    private UUID doctorId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "issued_by")
    private UUID issuedBy;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private ZonedDateTime issuedAt;

    @Column(name = "called_at")
    private ZonedDateTime calledAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;
}
