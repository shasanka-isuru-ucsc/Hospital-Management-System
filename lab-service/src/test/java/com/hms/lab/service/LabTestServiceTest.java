package com.hms.lab.service;

import com.hms.lab.dto.LabTestCreateRequest;
import com.hms.lab.dto.LabTestDto;
import com.hms.lab.dto.LabTestUpdateRequest;
import com.hms.lab.entity.LabTest;
import com.hms.lab.exception.BusinessException;
import com.hms.lab.exception.ResourceNotFoundException;
import com.hms.lab.repository.LabTestRepository;
import com.hms.lab.repository.OrderTestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabTestServiceTest {

    @Mock
    private LabTestRepository labTestRepository;

    @Mock
    private OrderTestRepository orderTestRepository;

    @InjectMocks
    private LabTestService labTestService;

    private UUID testId;
    private LabTest savedTest;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        savedTest = LabTest.builder()
                .id(testId)
                .name("Complete Blood Count")
                .code("CBC")
                .category("Haematology")
                .unitPrice(new BigDecimal("750.00"))
                .turnaroundHours(4)
                .referenceRange("WBC: 4.5-11.0 x10³/μL")
                .isActive(true)
                .build();
    }

    // ─── addTest ─────────────────────────────────────────────────────────────────

    @Test
    void addTest_withValidRequest_savesAndReturnsDto() {
        when(labTestRepository.existsByCode("CBC")).thenReturn(false);
        when(labTestRepository.save(any(LabTest.class))).thenReturn(savedTest);

        LabTestCreateRequest request = new LabTestCreateRequest();
        request.setName("Complete Blood Count");
        request.setCode("CBC");
        request.setCategory("Haematology");
        request.setUnitPrice(new BigDecimal("750.00"));

        LabTestDto result = labTestService.addTest(request);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Complete Blood Count");
        assertThat(result.getCode()).isEqualTo("CBC");
        assertThat(result.getIsActive()).isTrue();
        verify(labTestRepository).save(any(LabTest.class));
    }

    @Test
    void addTest_normalizesCodeToUpperCase() {
        when(labTestRepository.existsByCode("CBC")).thenReturn(false);
        when(labTestRepository.save(any(LabTest.class))).thenReturn(savedTest);

        LabTestCreateRequest request = new LabTestCreateRequest();
        request.setName("Complete Blood Count");
        request.setCode("cbc"); // lowercase
        request.setUnitPrice(new BigDecimal("750.00"));

        labTestService.addTest(request);

        verify(labTestRepository).existsByCode("CBC");
    }

    @Test
    void addTest_withDuplicateCode_throwsBusinessException() {
        when(labTestRepository.existsByCode("CBC")).thenReturn(true);

        LabTestCreateRequest request = new LabTestCreateRequest();
        request.setName("Complete Blood Count");
        request.setCode("CBC");
        request.setUnitPrice(new BigDecimal("750.00"));

        assertThatThrownBy(() -> labTestService.addTest(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DUPLICATE_TEST_CODE");
    }

    // ─── updateTest ──────────────────────────────────────────────────────────────

    @Test
    void updateTest_updatesOnlyProvidedFields() {
        when(labTestRepository.findById(testId)).thenReturn(Optional.of(savedTest));
        when(labTestRepository.save(any(LabTest.class))).thenReturn(savedTest);

        LabTestUpdateRequest request = new LabTestUpdateRequest();
        request.setUnitPrice(new BigDecimal("900.00"));

        LabTestDto result = labTestService.updateTest(testId, request);

        assertThat(result).isNotNull();
        verify(labTestRepository).save(savedTest);
        assertThat(savedTest.getUnitPrice()).isEqualByComparingTo(new BigDecimal("900.00"));
        assertThat(savedTest.getName()).isEqualTo("Complete Blood Count"); // unchanged
    }

    @Test
    void updateTest_whenNotFound_throwsResourceNotFoundException() {
        when(labTestRepository.findById(testId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labTestService.updateTest(testId, new LabTestUpdateRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Test not found");
    }

    @Test
    void updateTest_canDeactivateTest() {
        when(labTestRepository.findById(testId)).thenReturn(Optional.of(savedTest));
        when(labTestRepository.save(any(LabTest.class))).thenReturn(savedTest);

        LabTestUpdateRequest request = new LabTestUpdateRequest();
        request.setIsActive(false);

        labTestService.updateTest(testId, request);

        assertThat(savedTest.getIsActive()).isFalse();
    }

    // ─── deleteTest ───────────────────────────────────────────────────────────────

    @Test
    void deleteTest_whenNoOrders_softDeletesTest() {
        when(labTestRepository.findById(testId)).thenReturn(Optional.of(savedTest));
        when(orderTestRepository.existsByTestId(testId)).thenReturn(false);
        when(labTestRepository.save(any(LabTest.class))).thenReturn(savedTest);

        labTestService.deleteTest(testId);

        assertThat(savedTest.getIsActive()).isFalse();
        verify(labTestRepository).save(savedTest);
    }

    @Test
    void deleteTest_whenOrdersExist_throwsBusinessException() {
        when(labTestRepository.findById(testId)).thenReturn(Optional.of(savedTest));
        when(orderTestRepository.existsByTestId(testId)).thenReturn(true);

        assertThatThrownBy(() -> labTestService.deleteTest(testId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TEST_HAS_ORDERS");
    }

    @Test
    void deleteTest_whenNotFound_throwsResourceNotFoundException() {
        when(labTestRepository.findById(testId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labTestService.deleteTest(testId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── getTestCatalog ───────────────────────────────────────────────────────────

    @Test
    void getTestCatalog_returnsListOfDtos() {
        when(labTestRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(savedTest));

        List<LabTestDto> result = labTestService.getTestCatalog(null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("CBC");
    }
}
