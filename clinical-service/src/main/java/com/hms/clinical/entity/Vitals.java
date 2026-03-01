package com.hms.clinical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vitals")
@Getter
@Setter
public class Vitals {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    private Integer bpm;

    @Column
    private Double temperature;

    @Column(name = "blood_pressure_sys")
    private Integer bloodPressureSys;

    @Column(name = "blood_pressure_dia")
    private Integer bloodPressureDia;

    private Integer spo2;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Column(name = "blood_sugar")
    private Double bloodSugar;

    @CreationTimestamp
    @Column(name = "recorded_at", updatable = false, nullable = false)
    private LocalDateTime recordedAt;
}
