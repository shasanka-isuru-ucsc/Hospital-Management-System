package com.hms.ward.service;

import com.hms.ward.dto.WardCreateRequest;
import com.hms.ward.dto.WardDto;
import com.hms.ward.entity.Bed;
import com.hms.ward.entity.Ward;
import com.hms.ward.repository.BedRepository;
import com.hms.ward.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WardManagementService {

    private final WardRepository wardRepository;
    private final BedRepository bedRepository;

    // ─── List Wards ───────────────────────────────────────────────────────────────

    public List<WardDto> listWards(String type, Boolean isActive) {
        List<Ward> wards;

        if (type != null && isActive != null) {
            wards = wardRepository.findByTypeAndIsActive(type, isActive);
        } else if (type != null) {
            wards = wardRepository.findByType(type);
        } else if (isActive != null) {
            wards = wardRepository.findByIsActive(isActive);
        } else {
            wards = wardRepository.findAll();
        }

        return wards.stream().map(this::toDto).toList();
    }

    // ─── Create Ward ──────────────────────────────────────────────────────────────

    @Transactional
    public WardDto createWard(WardCreateRequest request) {
        Ward ward = Ward.builder()
                .name(request.getName())
                .type(request.getType())
                .capacity(request.getCapacity())
                .isActive(true)
                .build();

        ward = wardRepository.save(ward);

        // Auto-generate beds based on capacity
        String prefix = generateBedPrefix(ward.getName());
        List<Bed> beds = new ArrayList<>();
        for (int i = 0; i < request.getCapacity(); i++) {
            String bedNumber = prefix + "-" + String.format("%03d", 101 + i);
            beds.add(Bed.builder()
                    .wardId(ward.getId())
                    .bedNumber(bedNumber)
                    .status("available")
                    .build());
        }
        bedRepository.saveAll(beds);

        log.info("Created ward '{}' with {} beds ({}-101 to {}-{})",
                ward.getName(), request.getCapacity(), prefix, prefix, 100 + request.getCapacity());

        return toDto(ward);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    public WardDto toDto(Ward ward) {
        long occupied = bedRepository.countByWardIdAndStatus(ward.getId(), "occupied");
        long maintenance = bedRepository.countByWardIdAndStatus(ward.getId(), "maintenance");
        long reserved = bedRepository.countByWardIdAndStatus(ward.getId(), "reserved");
        long available = bedRepository.countByWardIdAndStatus(ward.getId(), "available");

        return WardDto.builder()
                .id(ward.getId())
                .name(ward.getName())
                .type(ward.getType())
                .capacity(ward.getCapacity())
                .occupied(occupied)
                .available(available)
                .maintenance(maintenance + reserved)
                .isActive(ward.getIsActive())
                .build();
    }

    private String generateBedPrefix(String wardName) {
        if (wardName == null || wardName.isBlank()) {
            return "B";
        }
        return String.valueOf(Character.toUpperCase(wardName.charAt(0)));
    }
}
