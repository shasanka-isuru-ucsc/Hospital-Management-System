package com.hms.clinical.service;

import com.hms.clinical.dto.*;
import com.hms.clinical.entity.Session;
import com.hms.clinical.entity.Vitals;
import com.hms.clinical.repository.SessionRepository;
import com.hms.clinical.repository.VitalsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientHistoryService {

    private final SessionRepository sessionRepository;
    private final SessionService sessionService;
    private final VitalsRepository vitalsRepository;

    public Map<String, Object> getPatientHistory(UUID patientId, String sessionType,
            LocalDate from, LocalDate to,
            int page, int limit) {

        Specification<Session> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("patientId"), patientId));
            if (sessionType != null)
                predicates.add(cb.equal(root.get("sessionType"), sessionType));
            if (from != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            if (to != null)
                predicates.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Session> sessionPage = sessionRepository.findAll(spec,
                PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<SessionDto> sessionDtos = sessionPage.getContent().stream()
                .map(sessionService::toDto)
                .collect(Collectors.toList());

        // Build vitals summary from latest vitals
        PatientHistoryDto.VitalsSummary vitalsSummary = buildVitalsSummary(patientId);

        // Build patient info from first session if available
        PatientHistoryDto.PatientInfo patientInfo = PatientHistoryDto.PatientInfo.builder()
                .id(patientId)
                .fullName(sessionPage.getContent().isEmpty() ? "" : sessionPage.getContent().get(0).getPatientName())
                .build();

        PatientHistoryDto historyDto = PatientHistoryDto.builder()
                .patient(patientInfo)
                .sessions(sessionDtos)
                .vitalsSummary(vitalsSummary)
                .build();

        Map<String, Object> result = new HashMap<>();
        result.put("data", historyDto);
        result.put("meta", Map.of("page", page, "total", sessionPage.getTotalElements()));
        return result;
    }

    private PatientHistoryDto.VitalsSummary buildVitalsSummary(UUID patientId) {
        // Get latest vitals across all sessions for this patient
        List<Vitals> allVitals = vitalsRepository
                .findBySessionPatientIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                        patientId,
                        java.time.LocalDateTime.of(2000, 1, 1, 0, 0),
                        java.time.LocalDateTime.now().plusDays(1));

        if (allVitals.isEmpty()) {
            return PatientHistoryDto.VitalsSummary.builder().build();
        }

        Vitals latest = allVitals.get(0);
        String bp = null;
        if (latest.getBloodPressureSys() != null && latest.getBloodPressureDia() != null) {
            bp = latest.getBloodPressureSys() + "/" + latest.getBloodPressureDia();
        }

        return PatientHistoryDto.VitalsSummary.builder()
                .latestBpm(latest.getBpm())
                .latestTemperature(latest.getTemperature())
                .latestBloodPressure(bp)
                .latestWeightKg(latest.getWeightKg())
                .build();
    }
}
