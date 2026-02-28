package com.hms.finance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices", schema = "finance")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 30)
    private String invoiceNumber; // e.g. INV-2026-00001

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "patient_name", nullable = false, length = 200)
    private String patientName; // Denormalized for audit integrity

    @Column(name = "billing_module", nullable = false, length = 30)
    private String billingModule; // opd | wound_care | channelling | lab | ward | pharmacy

    @Column(name = "session_reference_id")
    private UUID sessionReferenceId; // clinical session, lab order, ward admission ID

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LineItem> lineItems = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "discount_reason", columnDefinition = "TEXT")
    private String discountReason;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount; // subtotal - discount_amount

    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private String paymentStatus = "pending"; // pending | paid | partial | waived

    @Column(name = "payment_method", length = 20)
    private String paymentMethod; // cash | card | online | insurance

    @Column(nullable = false, length = 20)
    private String source; // event | manual

    @Column(name = "paid_at")
    private ZonedDateTime paidAt;

    @Column(name = "created_by", nullable = false, length = 200)
    private String createdBy; // staff name or 'system'

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
