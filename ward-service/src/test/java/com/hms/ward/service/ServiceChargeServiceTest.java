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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceChargeServiceTest {

    @Mock
    private WardServiceChargeRepository serviceChargeRepository;

    @Mock
    private AdmissionRepository admissionRepository;

    @InjectMocks
    private ServiceChargeService serviceChargeService;

    private UUID admissionId;
    private UUID serviceId;
    private Admission activeAdmission;
    private Admission dischargedAdmission;
    private WardServiceCharge savedCharge;

    @BeforeEach
    void setUp() {
        admissionId = UUID.randomUUID();
        serviceId = UUID.randomUUID();

        activeAdmission = Admission.builder()
                .id(admissionId)
                .patientId(UUID.randomUUID())
                .patientName("Andrea Lalema")
                .status("admitted")
                .admittedAt(ZonedDateTime.now().minusDays(2))
                .build();

        dischargedAdmission = Admission.builder()
                .id(admissionId)
                .patientName("Andrea Lalema")
                .status("discharged")
                .admittedAt(ZonedDateTime.now().minusDays(5))
                .dischargedAt(ZonedDateTime.now().minusDays(1))
                .build();

        savedCharge = WardServiceCharge.builder()
                .id(serviceId)
                .admissionId(admissionId)
                .serviceName("Daily Bed Charge")
                .serviceType("bed_charge")
                .quantity(1)
                .unitPrice(new BigDecimal("2500.00"))
                .totalPrice(new BigDecimal("2500.00"))
                .providedAt(ZonedDateTime.now())
                .addedBy("ward_staff")
                .build();
    }

    // ─── addServiceCharge ─────────────────────────────────────────────────────────

    @Test
    void addServiceCharge_toActiveAdmission_savesAndReturnsDto() {
        when(admissionRepository.findById(admissionId)).thenReturn(Optional.of(activeAdmission));
        when(serviceChargeRepository.save(any(WardServiceCharge.class))).thenReturn(savedCharge);

        WardServiceCreateRequest request = new WardServiceCreateRequest();
        request.setServiceName("Daily Bed Charge");
        request.setServiceType("bed_charge");
        request.setQuantity(1);
        request.setUnitPrice(new BigDecimal("2500.00"));

        WardServiceDto result = serviceChargeService.addServiceCharge(admissionId, request, "ward_staff");

        assertThat(result).isNotNull();
        assertThat(result.getServiceName()).isEqualTo("Daily Bed Charge");
        assertThat(result.getTotalPrice()).isEqualByComparingTo(new BigDecimal("2500.00"));
        verify(serviceChargeRepository).save(any(WardServiceCharge.class));
    }

    @Test
    void addServiceCharge_computesTotalPriceCorrectly() {
        WardServiceCharge medicationCharge = WardServiceCharge.builder()
                .id(UUID.randomUUID())
                .admissionId(admissionId)
                .serviceName("IV Ceftriaxone 1g")
                .serviceType("medication")
                .quantity(2)
                .unitPrice(new BigDecimal("450.00"))
                .totalPrice(new BigDecimal("900.00"))
                .providedAt(ZonedDateTime.now())
                .build();

        when(admissionRepository.findById(admissionId)).thenReturn(Optional.of(activeAdmission));
        when(serviceChargeRepository.save(any(WardServiceCharge.class))).thenReturn(medicationCharge);

        WardServiceCreateRequest request = new WardServiceCreateRequest();
        request.setServiceName("IV Ceftriaxone 1g");
        request.setServiceType("medication");
        request.setQuantity(2);
        request.setUnitPrice(new BigDecimal("450.00"));

        WardServiceDto result = serviceChargeService.addServiceCharge(admissionId, request, "doctor");

        // Verify total = 2 * 450 = 900
        assertThat(result.getTotalPrice()).isEqualByComparingTo(new BigDecimal("900.00"));
    }

    @Test
    void addServiceCharge_toDischargedAdmission_throwsBusinessException() {
        when(admissionRepository.findById(admissionId)).thenReturn(Optional.of(dischargedAdmission));

        WardServiceCreateRequest request = new WardServiceCreateRequest();
        request.setServiceName("Daily Bed Charge");
        request.setServiceType("bed_charge");
        request.setQuantity(1);
        request.setUnitPrice(new BigDecimal("2500.00"));

        assertThatThrownBy(() -> serviceChargeService.addServiceCharge(admissionId, request, "ward_staff"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ADMISSION_DISCHARGED");
    }

    @Test
    void addServiceCharge_whenAdmissionNotFound_throwsResourceNotFoundException() {
        when(admissionRepository.findById(admissionId)).thenReturn(Optional.empty());

        WardServiceCreateRequest request = new WardServiceCreateRequest();
        request.setServiceName("Daily Bed Charge");
        request.setServiceType("bed_charge");
        request.setQuantity(1);
        request.setUnitPrice(new BigDecimal("2500.00"));

        assertThatThrownBy(() -> serviceChargeService.addServiceCharge(admissionId, request, "ward_staff"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Admission not found");
    }

    // ─── getServicesForAdmission ──────────────────────────────────────────────────

    @Test
    void getServicesForAdmission_returnsServiceListWithRunningTotal() {
        WardServiceCharge charge2 = WardServiceCharge.builder()
                .id(UUID.randomUUID())
                .admissionId(admissionId)
                .serviceName("IV Ceftriaxone")
                .serviceType("medication")
                .quantity(2)
                .unitPrice(new BigDecimal("450.00"))
                .totalPrice(new BigDecimal("900.00"))
                .providedAt(ZonedDateTime.now())
                .build();

        when(admissionRepository.findById(admissionId)).thenReturn(Optional.of(activeAdmission));
        when(serviceChargeRepository.findByAdmissionIdOrderByProvidedAtAsc(admissionId))
                .thenReturn(List.of(savedCharge, charge2));

        ServiceListResponseData result = serviceChargeService.getServicesForAdmission(admissionId, null);

        assertThat(result.getServices()).hasSize(2);
        assertThat(result.getRunningTotal())
                .isEqualByComparingTo(new BigDecimal("3400.00")); // 2500 + 900
        assertThat(result.getPatientName()).isEqualTo("Andrea Lalema");
    }

    // ─── removeServiceCharge ─────────────────────────────────────────────────────

    @Test
    void removeServiceCharge_fromActiveAdmission_removesAndReturnsUpdatedTotal() {
        when(admissionRepository.findById(admissionId)).thenReturn(Optional.of(activeAdmission));
        when(serviceChargeRepository.findByIdAndAdmissionId(serviceId, admissionId))
                .thenReturn(Optional.of(savedCharge));
        doNothing().when(serviceChargeRepository).delete(savedCharge);
        when(serviceChargeRepository.findByAdmissionIdOrderByProvidedAtAsc(admissionId))
                .thenReturn(List.of()); // All removed

        BigDecimal result = serviceChargeService.removeServiceCharge(admissionId, serviceId);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        verify(serviceChargeRepository).delete(savedCharge);
    }

    @Test
    void removeServiceCharge_fromDischargedAdmission_throwsBusinessException() {
        when(admissionRepository.findById(admissionId)).thenReturn(Optional.of(dischargedAdmission));

        assertThatThrownBy(() -> serviceChargeService.removeServiceCharge(admissionId, serviceId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ADMISSION_DISCHARGED");
    }

    @Test
    void removeServiceCharge_whenServiceNotFound_throwsResourceNotFoundException() {
        when(admissionRepository.findById(admissionId)).thenReturn(Optional.of(activeAdmission));
        when(serviceChargeRepository.findByIdAndAdmissionId(serviceId, admissionId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceChargeService.removeServiceCharge(admissionId, serviceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Service charge not found");
    }
}
