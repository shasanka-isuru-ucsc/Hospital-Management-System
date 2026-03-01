package com.hms.ward.service;

import com.hms.ward.dto.BedDto;
import com.hms.ward.dto.BedStatusUpdateRequest;
import com.hms.ward.entity.Bed;
import com.hms.ward.entity.Ward;
import com.hms.ward.exception.BusinessException;
import com.hms.ward.exception.ResourceNotFoundException;
import com.hms.ward.repository.BedRepository;
import com.hms.ward.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BedService {

    private final BedRepository bedRepository;
    private final WardRepository wardRepository;

    // ─── List Beds ────────────────────────────────────────────────────────────────

    public List<BedDto> listBeds(UUID wardId, String status) {
        List<Bed> beds;

        if (wardId != null && status != null) {
            beds = bedRepository.findByWardIdAndStatusOrderByBedNumber(wardId, status);
        } else if (wardId != null) {
            beds = bedRepository.findByWardIdOrderByBedNumber(wardId);
        } else if (status != null) {
            beds = bedRepository.findByStatusOrderByBedNumber(status);
        } else {
            beds = bedRepository.findAll();
        }

        // Build a ward name map for denormalization
        Map<UUID, String> wardNameMap = wardRepository.findAll().stream()
                .collect(Collectors.toMap(Ward::getId, Ward::getName));

        return beds.stream()
                .map(bed -> toDto(bed, wardNameMap.getOrDefault(bed.getWardId(), "")))
                .toList();
    }

    // ─── Update Bed Status ────────────────────────────────────────────────────────

    @Transactional
    public BedDto updateBedStatus(UUID bedId, BedStatusUpdateRequest request) {
        Bed bed = bedRepository.findById(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found: " + bedId));

        // Cannot manually set to 'occupied' — this happens via admissions
        if ("occupied".equals(request.getStatus())) {
            throw new BusinessException("INVALID_STATUS",
                    "Cannot manually set bed status to 'occupied'. Use the admission API.", 422);
        }

        // Cannot change status of an occupied bed manually
        if ("occupied".equals(bed.getStatus())) {
            throw new BusinessException("BED_OCCUPIED",
                    "Cannot change status of an occupied bed. Discharge the patient first.", 422);
        }

        bed.setStatus(request.getStatus());
        if (request.getNotes() != null) {
            bed.setNotes(request.getNotes());
        }
        bed = bedRepository.save(bed);

        log.info("Bed {} status updated to {}", bed.getBedNumber(), bed.getStatus());

        Ward ward = wardRepository.findById(bed.getWardId()).orElse(null);
        String wardName = ward != null ? ward.getName() : "";
        return toDto(bed, wardName);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    public BedDto toDto(Bed bed, String wardName) {
        return BedDto.builder()
                .id(bed.getId())
                .wardId(bed.getWardId())
                .wardName(wardName)
                .bedNumber(bed.getBedNumber())
                .status(bed.getStatus())
                .currentAdmissionId(bed.getCurrentAdmissionId())
                .currentPatientName(bed.getCurrentPatientName())
                .build();
    }
}
