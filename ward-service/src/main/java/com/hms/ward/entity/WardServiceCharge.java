package com.hms.ward.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "ward_services", schema = "ward")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WardServiceCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admission_id", nullable = false)
    private UUID admissionId;

    @Column(name = "service_name", nullable = false, length = 200)
    private String serviceName;

    @Column(name = "service_type", nullable = false, length = 50)
    private String serviceType; // medication | procedure | bed_charge | investigation | meal | other

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "provided_at", nullable = false)
    private ZonedDateTime providedAt;

    @Column(name = "added_by", length = 200)
    private String addedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
