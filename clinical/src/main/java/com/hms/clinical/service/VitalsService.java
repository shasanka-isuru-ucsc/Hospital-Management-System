package com.hms.clinical.service;

import com.hms.clinical.dto.VitalsCreateRequest;
import com.hms.clinical.dto.VitalsDto;
import com.hms.clinical.entity.Session;
import com.hms.clinical.entity.Vitals;
import com.hms.clinical.exception.BusinessException;
import com.hms.clinical.exception.ResourceNotFoundException;
import com.hms.clinical.repository.VitalsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VitalsService {

    private final VitalsRepository vitalsRepository;
    private final SessionService sessionService;

    @Transactional
    public VitalsDto recordVitals(UUID sessionId, VitalsCreateRequest request) {
        Session session = sessionService.getSessionEntity(sessionId);

        if ("completed".equals(session.getStatus())) {
            throw new BusinessException("Cannot record vitals for a completed session");
        }

        // Upsert — one vitals record per session
        Optional<Vitals> existing = vitalsRepository.findBySessionId(sessionId);
        Vitals vitals = existing.orElseGet(Vitals::new);
        vitals.setSession(session);

        if (request.getBpm() != null)
            vitals.setBpm(request.getBpm());
        if (request.getTemperature() != null)
            vitals.setTemperature(request.getTemperature());
        if (request.getBloodPressureSys() != null)
            vitals.setBloodPressureSys(request.getBloodPressureSys());
        if (request.getBloodPressureDia() != null)
            vitals.setBloodPressureDia(request.getBloodPressureDia());
        if (request.getSpo2() != null)
            vitals.setSpo2(request.getSpo2());
        if (request.getWeightKg() != null)
            vitals.setWeightKg(request.getWeightKg());
        if (request.getHeightCm() != null)
            vitals.setHeightCm(request.getHeightCm());
        if (request.getBloodSugar() != null)
            vitals.setBloodSugar(request.getBloodSugar());

        Vitals saved = vitalsRepository.save(vitals);
        return toDto(saved);
    }

    public VitalsDto getVitals(UUID sessionId) {
        Vitals vitals = vitalsRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Vitals not found for session: " + sessionId));
        return toDto(vitals);
    }

    public List<VitalsDto> getVitalsHistory(UUID patientId, LocalDate from, LocalDate to) {
        LocalDateTime start = (from != null) ? from.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime end = (to != null) ? to.plusDays(1).atStartOfDay() : LocalDateTime.now().plusDays(1);

        return vitalsRepository.findBySessionPatientIdAndRecordedAtBetweenOrderByRecordedAtDesc(patientId, start, end)
                .stream()
                .map(v -> {
                    VitalsDto dto = toDto(v);
                    dto.setSessionDate(v.getSession().getCreatedAt().toLocalDate());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private VitalsDto toDto(Vitals v) {
        return VitalsDto.builder()
                .id(v.getId())
                .sessionId(v.getSession().getId())
                .bpm(v.getBpm())
                .temperature(v.getTemperature())
                .bloodPressureSys(v.getBloodPressureSys())
                .bloodPressureDia(v.getBloodPressureDia())
                .spo2(v.getSpo2())
                .weightKg(v.getWeightKg())
                .heightCm(v.getHeightCm())
                .bloodSugar(v.getBloodSugar())
                .recordedAt(v.getRecordedAt())
                .build();
    }
}
