package com.hms.clinical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lab_requests")
@Getter
@Setter
public class LabRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(name = "test_name", nullable = false)
    private String testName;

    @Column(nullable = false, length = 20)
    private String urgency = "routine"; // routine, urgent

    @Column(name = "lab_order_id")
    private UUID labOrderId;

    @Column(name = "order_status", length = 20)
    private String orderStatus; // registered, processing, completed, cancelled

    @CreationTimestamp
    @Column(name = "requested_at", updatable = false, nullable = false)
    private LocalDateTime requestedAt;
}
