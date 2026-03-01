package com.hms.staff.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "nurse_allocations", schema = "staff")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NurseAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nurse_id", nullable = false)
    private UUID nurseId;

    @Column(name = "nurse_name", nullable = false, length = 200)
    private String nurseName;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "doctor_name", nullable = false, length = 200)
    private String doctorName;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "active"; // active | completed | cancelled

    @CreationTimestamp
    @Column(name = "allocated_at", nullable = false, updatable = false)
    private ZonedDateTime allocatedAt;

    @Column(name = "allocated_by", length = 200)
    private String allocatedBy;
}
