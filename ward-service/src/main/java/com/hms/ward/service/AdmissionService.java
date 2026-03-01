package com.hms.ward.service;

import com.hms.ward.dto.*;
import com.hms.ward.entity.Admission;
import com.hms.ward.entity.Bed;
import com.hms.ward.entity.Ward;
import com.hms.ward.entity.WardServiceCharge;
import com.hms.ward.event.BillingWardEventPublisher;
import com.hms.ward.exception.BusinessException;
import com.hms.ward.exception.ResourceNotFoundException;
import com.hms.ward.repository.AdmissionRepository;
import com.hms.ward.repository.BedRepository;
import com.hms.ward.repository.WardRepository;
import com.hms.ward.repository.WardServiceChargeRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionService {

    private final AdmissionRepository admissionRepository;
    private final BedRepository bedRepository;
    private final WardRepository wardRepository;
    private final WardServiceChargeRepository serviceChargeRepository;
    private final BillingWardEventPublisher billingWardEventPublisher;

    // ─── Admit Patient ────────────────────────────────────────────────────────────

    @Transactional
    public AdmissionDto admitPatient(AdmissionCreateRequest request) {
        // 1. Verify bed exists and is available
        Bed bed = bedRepository.findById(request.getBedId())
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found: " + request.getBedId()));

        if (!"available".equals(bed.getStatus())) {
            throw new BusinessException("BED_NOT_AVAILABLE",
                    "Bed " + bed.getBedNumber() + " is currently " + bed.getStatus(), 422);
        }

        // 2. Check patient does not already have an active admission
        admissionRepository.findByPatientIdAndStatus(request.getPatientId(), "admitted")
                .ifPresent(existing -> {
                    throw new BusinessException("PATIENT_ALREADY_ADMITTED",
                            "This patient already has an active admission", 422);
                });

        // 3. Resolve ward
        Ward ward = wardRepository.findById(bed.getWardId())
                .orElseThrow(() -> new ResourceNotFoundException("Ward not found for bed"));

        // 4. Resolve patient name fallback
        String patientName = (request.getPatientName() != null && !request.getPatientName().isBlank())
                ? request.getPatientName()
                : "Patient " + request.getPatientId();

        // 5. Create admission
        Admission admission = Admission.builder()
                .patientId(request.getPatientId())
                .patientName(patientName)
                .patientNumber(request.getPatientNumber())
                .bedId(bed.getId())
                .wardId(ward.getId())
                .attendingDoctorId(request.getAttendingDoctorId())
                .attendingDoctorName(request.getAttendingDoctorName())
                .admissionReason(request.getAdmissionReason())
                .notes(request.getNotes())
                .status("admitted")
                .build();

        admission = admissionRepository.save(admission);

        // 6. Update bed to occupied
        bed.setStatus("occupied");
        bed.setCurrentAdmissionId(admission.getId());
        bed.setCurrentPatientName(patientName);
        bedRepository.save(bed);

        log.info("Patient '{}' admitted to bed {} in ward '{}'",
                patientName, bed.getBedNumber(), ward.getName());

        return toDto(admission, bed, ward, List.of());
    }

    // ─── List Admissions ──────────────────────────────────────────────────────────

    public Page<AdmissionDto> listAdmissions(UUID wardId, String status, UUID attendingDoctorId,
                                              LocalDate from, LocalDate to, int page, int limit) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("admittedAt").descending());

        Specification<Admission> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (wardId != null) {
                predicates.add(cb.equal(root.get("wardId"), wardId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (attendingDoctorId != null) {
                predicates.add(cb.equal(root.get("attendingDoctorId"), attendingDoctorId));
            }
            if (from != null) {
                ZonedDateTime fromDt = from.atStartOfDay(ZoneId.systemDefault());
                predicates.add(cb.greaterThanOrEqualTo(root.get("admittedAt"), fromDt));
            }
            if (to != null) {
                ZonedDateTime toDt = to.plusDays(1).atStartOfDay(ZoneId.systemDefault());
                predicates.add(cb.lessThan(root.get("admittedAt"), toDt));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Admission> admissionsPage = admissionRepository.findAll(spec, pageable);

        // Pre-fetch beds and wards for the page
        Map<UUID, Bed> bedMap = fetchBeds(admissionsPage.getContent());
        Map<UUID, Ward> wardMap = fetchWards(admissionsPage.getContent());

        return admissionsPage.map(admission -> {
            Bed bed = bedMap.get(admission.getBedId());
            Ward ward = wardMap.get(admission.getWardId());
            List<WardServiceCharge> services = serviceChargeRepository
                    .findByAdmissionIdOrderByProvidedAtAsc(admission.getId());
            return toDto(admission, bed, ward, services);
        });
    }

    // ─── Get Admission by ID ──────────────────────────────────────────────────────

    public AdmissionDto getAdmissionById(UUID id) {
        Admission admission = admissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + id));

        Bed bed = bedRepository.findById(admission.getBedId()).orElse(null);
        Ward ward = wardRepository.findById(admission.getWardId()).orElse(null);
        List<WardServiceCharge> services = serviceChargeRepository
                .findByAdmissionIdOrderByProvidedAtAsc(id);

        return toDto(admission, bed, ward, services);
    }

    // ─── Discharge Patient ────────────────────────────────────────────────────────

    @Transactional
    public AdmissionDto dischargePatient(UUID admissionId, DischargeRequest request) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + admissionId));

        if ("discharged".equals(admission.getStatus())) {
            throw new BusinessException("ALREADY_DISCHARGED",
                    "Patient is already discharged", 422);
        }

        // 1. Update admission status
        ZonedDateTime now = ZonedDateTime.now();
        admission.setStatus("discharged");
        admission.setDischargedAt(now);
        if (request != null) {
            admission.setDischargeNotes(request.getDischargeNotes());
            admission.setDischargeDiagnosis(request.getDischargeDiagnosis());
        }
        admission = admissionRepository.save(admission);

        // 2. Release the bed
        Bed bed = bedRepository.findById(admission.getBedId()).orElse(null);
        if (bed != null) {
            bed.setStatus("available");
            bed.setCurrentAdmissionId(null);
            bed.setCurrentPatientName(null);
            bedRepository.save(bed);
        }

        // 3. Compute total from all ward services
        List<WardServiceCharge> services = serviceChargeRepository
                .findByAdmissionIdOrderByProvidedAtAsc(admissionId);

        BigDecimal total = services.stream()
                .map(WardServiceCharge::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Publish billing.ward event to Finance Service
        Ward ward = wardRepository.findById(admission.getWardId()).orElse(null);
        billingWardEventPublisher.publishBillingWard(admission, ward, bed, services, total);

        log.info("Patient '{}' discharged from admission {}. Total: {}",
                admission.getPatientName(), admissionId, total);

        return toDto(admission, bed, ward, services);
    }

    // ─── Get Patient Admission History ────────────────────────────────────────────

    public Page<AdmissionDto> getPatientAdmissionHistory(UUID patientId, int page, int limit) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("admittedAt").descending());

        Specification<Admission> spec = (root, query, cb) ->
                cb.equal(root.get("patientId"), patientId);

        Page<Admission> admissionsPage = admissionRepository.findAll(spec, pageable);

        Map<UUID, Bed> bedMap = fetchBeds(admissionsPage.getContent());
        Map<UUID, Ward> wardMap = fetchWards(admissionsPage.getContent());

        return admissionsPage.map(admission -> {
            Bed bed = bedMap.get(admission.getBedId());
            Ward ward = wardMap.get(admission.getWardId());
            List<WardServiceCharge> services = serviceChargeRepository
                    .findByAdmissionIdOrderByProvidedAtAsc(admission.getId());
            return toDto(admission, bed, ward, services);
        });
    }

    // ─── DTO Helpers ──────────────────────────────────────────────────────────────

    public AdmissionDto toDto(Admission admission, Bed bed, Ward ward,
                               List<WardServiceCharge> services) {
        BigDecimal runningTotal = services.stream()
                .map(WardServiceCharge::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ZonedDateTime endTime = admission.getDischargedAt() != null
                ? admission.getDischargedAt()
                : ZonedDateTime.now();
        long daysAdmitted = admission.getAdmittedAt() != null
                ? ChronoUnit.DAYS.between(admission.getAdmittedAt().toLocalDate(), endTime.toLocalDate())
                : 0;

        List<WardServiceDto> serviceDtos = services.stream()
                .map(this::toServiceDto)
                .toList();

        return AdmissionDto.builder()
                .id(admission.getId())
                .patientId(admission.getPatientId())
                .patientName(admission.getPatientName())
                .patientNumber(admission.getPatientNumber())
                .bedId(admission.getBedId())
                .bedNumber(bed != null ? bed.getBedNumber() : null)
                .wardId(admission.getWardId())
                .wardName(ward != null ? ward.getName() : null)
                .attendingDoctorId(admission.getAttendingDoctorId())
                .attendingDoctorName(admission.getAttendingDoctorName())
                .admissionReason(admission.getAdmissionReason())
                .notes(admission.getNotes())
                .status(admission.getStatus())
                .admittedAt(admission.getAdmittedAt())
                .dischargedAt(admission.getDischargedAt())
                .dischargeNotes(admission.getDischargeNotes())
                .dischargeDiagnosis(admission.getDischargeDiagnosis())
                .services(serviceDtos)
                .runningTotal(runningTotal)
                .daysAdmitted(daysAdmitted)
                .build();
    }

    public WardServiceDto toServiceDto(WardServiceCharge charge) {
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

    private Map<UUID, Bed> fetchBeds(List<Admission> admissions) {
        List<UUID> bedIds = admissions.stream().map(Admission::getBedId).distinct().toList();
        return bedRepository.findAllById(bedIds).stream()
                .collect(Collectors.toMap(Bed::getId, b -> b));
    }

    private Map<UUID, Ward> fetchWards(List<Admission> admissions) {
        List<UUID> wardIds = admissions.stream().map(Admission::getWardId).distinct().toList();
        return wardRepository.findAllById(wardIds).stream()
                .collect(Collectors.toMap(Ward::getId, w -> w));
    }
}
