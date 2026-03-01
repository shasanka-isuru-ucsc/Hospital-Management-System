package com.hms.lab.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", schema = "lab")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "patient_name", nullable = false, length = 200)
    private String patientName; // Denormalized

    @Column(name = "patient_mobile", length = 20)
    private String patientMobile;

    @Column(name = "session_id")
    private UUID sessionId; // Set when order created from clinical event

    @Column(name = "source", nullable = false, length = 20)
    private String source; // walk_in | clinical_request

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "registered"; // registered | processing | completed | cancelled

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private String paymentStatus = "pending"; // pending | paid

    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "report_file_key", columnDefinition = "TEXT")
    private String reportFileKey; // MinIO object key

    @Column(name = "report_notes", columnDefinition = "TEXT")
    private String reportNotes;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes; // Technician instructions at order creation

    @Column(name = "created_by", nullable = false, length = 200)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Column(name = "paid_at")
    private ZonedDateTime paidAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderTest> tests = new ArrayList<>();
}
