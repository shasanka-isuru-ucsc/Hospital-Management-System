package com.hms.lab.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_tests", schema = "lab")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private LabOrder order;

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(name = "test_name", nullable = false, length = 200)
    private String testName; // Denormalized

    @Column(name = "test_code", nullable = false, length = 20)
    private String testCode; // Denormalized

    @Column(name = "urgency", nullable = false, length = 20)
    @Builder.Default
    private String urgency = "routine"; // routine | urgent

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice; // Snapshot at order time

    @Column(name = "result_value", columnDefinition = "TEXT")
    private String resultValue;

    @Column(name = "is_abnormal")
    private Boolean isAbnormal;

    @Column(name = "technician_notes", columnDefinition = "TEXT")
    private String technicianNotes;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "pending"; // pending | completed
}
