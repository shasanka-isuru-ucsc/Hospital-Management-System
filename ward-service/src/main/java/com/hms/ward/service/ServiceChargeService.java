package com.hms.ward.service;

import com.hms.ward.dto.ServiceListResponseData;
import com.hms.ward.dto.WardServiceCreateRequest;
import com.hms.ward.dto.WardServiceDto;
import com.hms.ward.entity.Admission;
import com.hms.ward.entity.WardServiceCharge;
import com.hms.ward.exception.BusinessException;
import com.hms.ward.exception.ResourceNotFoundException;
import com.hms.ward.repository.AdmissionRepository;
import com.hms.ward.repository.WardServiceChargeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceChargeService {

    private final WardServiceChargeRepository serviceChargeRepository;
    private final AdmissionRepository admissionRepository;

    // ─── Add Service Charge ───────────────────────────────────────────────────────

    @Transactional
    public WardServiceDto addServiceCharge(UUID admissionId, WardServiceCreateRequest request, String addedBy) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + admissionId));

        if ("discharged".equals(admission.getStatus())) {
            throw new BusinessException("ADMISSION_DISCHARGED",
                    "Cannot add services to a discharged admission", 422);
        }

        BigDecimal totalPrice = request.getUnitPrice()
                .multiply(BigDecimal.valueOf(request.getQuantity()));

        ZonedDateTime providedAt = request.getProvidedAt() != null
                ? request.getProvidedAt()
                : ZonedDateTime.now();

        WardServiceCharge charge = WardServiceCharge.builder()
                .admissionId(admissionId)
                .serviceName(request.getServiceName())
                .serviceType(request.getServiceType())
                .quantity(request.getQuantity())
                .unitPrice(request.getUnitPrice())
                .totalPrice(totalPrice)
                .providedAt(providedAt)
                .addedBy(addedBy)
                .notes(request.getNotes())
                .build();

        charge = serviceChargeRepository.save(charge);

        log.info("Service '{}' added to admission {} by {}",
                request.getServiceName(), admissionId, addedBy);

        return toDto(charge);
    }

    // ─── Get All Services for Admission ──────────────────────────────────────────

    public ServiceListResponseData getServicesForAdmission(UUID admissionId, String serviceType) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + admissionId));

        List<WardServiceCharge> charges;
        if (serviceType != null) {
            charges = serviceChargeRepository
                    .findByAdmissionIdAndServiceTypeOrderByProvidedAtAsc(admissionId, serviceType);
        } else {
            charges = serviceChargeRepository
                    .findByAdmissionIdOrderByProvidedAtAsc(admissionId);
        }

        BigDecimal runningTotal = charges.stream()
                .map(WardServiceCharge::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ZonedDateTime endTime = admission.getDischargedAt() != null
                ? admission.getDischargedAt()
                : ZonedDateTime.now();
        long daysAdmitted = admission.getAdmittedAt() != null
                ? ChronoUnit.DAYS.between(admission.getAdmittedAt().toLocalDate(), endTime.toLocalDate())
                : 0;

        return ServiceListResponseData.builder()
                .admissionId(admissionId)
                .patientName(admission.getPatientName())
                .admittedAt(admission.getAdmittedAt())
                .daysAdmitted(daysAdmitted)
                .services(charges.stream().map(this::toDto).toList())
                .runningTotal(runningTotal)
                .build();
    }

    // ─── Remove Service Charge ────────────────────────────────────────────────────

    @Transactional
    public BigDecimal removeServiceCharge(UUID admissionId, UUID serviceId) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + admissionId));

        if ("discharged".equals(admission.getStatus())) {
            throw new BusinessException("ADMISSION_DISCHARGED",
                    "Cannot remove services from a discharged admission", 422);
        }

        WardServiceCharge charge = serviceChargeRepository
                .findByIdAndAdmissionId(serviceId, admissionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service charge not found: " + serviceId));

        serviceChargeRepository.delete(charge);

        // Compute updated running total
        List<WardServiceCharge> remaining = serviceChargeRepository
                .findByAdmissionIdOrderByProvidedAtAsc(admissionId);

        BigDecimal runningTotal = remaining.stream()
                .map(WardServiceCharge::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("Service charge {} removed from admission {}", serviceId, admissionId);
        return runningTotal;
    }

    // ─── DTO Helper ───────────────────────────────────────────────────────────────

    private WardServiceDto toDto(WardServiceCharge charge) {
        return WardServiceDto.builder()
                .id(charge.getId())
                .admissionId(charge.getAdmissionId())
                .serviceName(charge.getServiceName())
                .serviceType(charge.getServiceType())
                .quantity(charge.getQuantity())
                .unitPrice(charge.getUnitPrice())
                .totalPrice(charge.getTotalPrice())
                .providedAt(charge.getProvidedAt())
                .addedBy(charge.getAddedBy())
                .notes(charge.getNotes())
                .build();
    }
}
