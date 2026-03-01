package com.hms.lab.service;

import com.hms.lab.dto.LabTestCreateRequest;
import com.hms.lab.dto.LabTestDto;
import com.hms.lab.dto.LabTestUpdateRequest;
import com.hms.lab.entity.LabTest;
import com.hms.lab.exception.BusinessException;
import com.hms.lab.exception.ResourceNotFoundException;
import com.hms.lab.repository.LabTestRepository;
import com.hms.lab.repository.OrderTestRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabTestService {

    private final LabTestRepository labTestRepository;
    private final OrderTestRepository orderTestRepository;

    // ─── Get Test Catalog ────────────────────────────────────────────────────────

    public List<LabTestDto> getTestCatalog(String category, String search, Boolean isActive) {
        Specification<LabTest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Default to active tests only if not explicitly specified
            boolean activeFilter = isActive == null ? true : isActive;
            predicates.add(cb.equal(root.get("isActive"), activeFilter));

            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("code")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return labTestRepository.findAll(spec).stream().map(this::toDto).toList();
    }

    // ─── Add Test ────────────────────────────────────────────────────────────────

    @Transactional
    public LabTestDto addTest(LabTestCreateRequest request) {
        if (labTestRepository.existsByCode(request.getCode().toUpperCase())) {
            throw new BusinessException("DUPLICATE_TEST_CODE",
                    "Test code already exists: " + request.getCode(), 409);
        }

        LabTest test = LabTest.builder()
                .name(request.getName())
                .code(request.getCode().toUpperCase())
                .category(request.getCategory())
                .unitPrice(request.getUnitPrice())
                .turnaroundHours(request.getTurnaroundHours())
                .referenceRange(request.getReferenceRange())
                .isActive(true)
                .build();

        LabTest saved = labTestRepository.save(test);
        log.info("Added test to catalog: {} ({})", saved.getName(), saved.getCode());
        return toDto(saved);
    }

    // ─── Update Test ─────────────────────────────────────────────────────────────

    @Transactional
    public LabTestDto updateTest(UUID id, LabTestUpdateRequest request) {
        LabTest test = labTestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Test not found: " + id));

        if (request.getName() != null) test.setName(request.getName());
        if (request.getCategory() != null) test.setCategory(request.getCategory());
        if (request.getUnitPrice() != null) test.setUnitPrice(request.getUnitPrice());
        if (request.getTurnaroundHours() != null) test.setTurnaroundHours(request.getTurnaroundHours());
        if (request.getReferenceRange() != null) test.setReferenceRange(request.getReferenceRange());
        if (request.getIsActive() != null) test.setIsActive(request.getIsActive());

        LabTest saved = labTestRepository.save(test);
        log.info("Updated test: {} ({})", saved.getName(), saved.getCode());
        return toDto(saved);
    }

    // ─── Delete Test (Soft) ───────────────────────────────────────────────────────

    @Transactional
    public void deleteTest(UUID id) {
        LabTest test = labTestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Test not found: " + id));

        if (orderTestRepository.existsByTestId(id)) {
            throw new BusinessException("TEST_HAS_ORDERS",
                    "Cannot delete test — orders exist for this test", 422);
        }

        test.setIsActive(false);
        labTestRepository.save(test);
        log.info("Soft-deleted test: {} ({})", test.getName(), test.getCode());
    }

    // ─── Mapper ──────────────────────────────────────────────────────────────────

    public LabTestDto toDto(LabTest test) {
        return LabTestDto.builder()
                .id(test.getId())
                .name(test.getName())
                .code(test.getCode())
                .category(test.getCategory())
                .unitPrice(test.getUnitPrice())
                .turnaroundHours(test.getTurnaroundHours())
                .referenceRange(test.getReferenceRange())
                .isActive(test.getIsActive())
                .build();
    }
}
