package com.hms.ward.service;

import com.hms.ward.dto.AdmissionCreateRequest;
import com.hms.ward.dto.AdmissionDto;
import com.hms.ward.dto.DischargeRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdmissionServiceTest {

    @Mock
    private AdmissionRepository admissionRepository;

    @Mock
    private BedRepository bedRepository;

    @Mock
    private WardRepository wardRepository;

    @Mock
    private WardServiceChargeRepository serviceChargeRepository;

    @Mock
    private BillingWardEventPublisher billingWardEventPublisher;

    @InjectMocks
    private AdmissionService admissionService;

    private UUID patientId;
    private UUID bedId;
    private UUID wardId;
    private UUID admissionId;
    private Bed availableBed;
    private Ward ward;
    private Admission savedAdmission;

    @BeforeEach
    void setUp() {
        patientId = UUID.randomUUID();
        bedId = UUID.randomUUID();
        wardId = UUID.randomUUID();
        admissionId = UUID.randomUUID();

        ward = Ward.builder()
                .id(wardId)
                .name("Male General Ward")
                .type("general")
                .capacity(20)
                .isActive(true)
                .build();

        availableBed = Bed.builder()
                .id(bedId)
                .wardId(wardId)
                .bedNumber("M-101")
                .status("available")
                .build();

        savedAdmission = Admission.builder()
                .id(admissionId)
                .patientId(patientId)
                .patientName("Andrea Lalema")
                .patientNumber("R00001")
                .bedId(bedId)
                .wardId(wardId)
                .attendingDoctorId(UUID.randomUUID())
                .attendingDoctorName("Dr. Smith")
                .admissionReason("Typhoid fever")
                .status("admitted")
                .admittedAt(ZonedDateTime.now().minusDays(3))
                .build();
    }

    // ─── admitPatient ─────────────────────────────────────────────────────────────

    @Test
    void admitPatient_withAvailableBed_createsAdmission() {
        when(bedRepository.findById(bedId)).thenReturn(Optional.of(availableBed));
        when(admissionRepository.findByPatientIdAndStatus(patientId, "admitted"))
                .thenReturn(Optional.empty());
        when(wardRepository.findById(wardId)).thenReturn(Optional.of(ward));
        when(admissionRepository.save(any(Admission.class))).thenReturn(savedAdmission);
        when(bedRepository.save(any(Bed.class))).thenReturn(availableBed);

        AdmissionCreateRequest request = new AdmissionCreateRequest();
        request.setPatientId(patientId);
        request.setPatientName("Andrea Lalema");
        request.setPatientNumber("R00001");
        request.setBedId(bedId);
        request.setAttendingDoctorId(UUID.randomUUID());
        request.setAdmissionReason("Typhoid fever");

        AdmissionDto result = admissionService.admitPatient(request);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("admitted");
        assertThat(result.getPatientName()).isEqualTo("Andrea Lalema");
        verify(admissionRepository).save(any(Admission.class));
        verify(bedRepository).save(any(Bed.class));
    }

    @Test
    void admitPatient_whenBedOccupied_throwsBusinessException() {
        availableBed.setStatus("occupied");
        when(bedRepository.findById(bedId)).thenReturn(Optional.of(availableBed));

        AdmissionCreateRequest request = new AdmissionCreateRequest();
        request.setPatientId(patientId);
        request.setBedId(bedId);
        request.setAttendingDoctorId(UUID.randomUUID());
        request.setAdmissionReason("Typhoid fever");

        assertThatThrownBy(() -> admissionService.admitPatient(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "BED_NOT_AVAILABLE");
    }

    @Test
    void admitPatient_whenPatientAlreadyAdmitted_throwsBusinessException() {
        when(bedRepository.findById(bedId)).thenReturn(Optional.of(availableBed));
        when(admissionRepository.findByPatientIdAndStatus(patientId, "admitted"))
                .thenReturn(Optional.of(savedAdmission));

        AdmissionCreateRequest request = new AdmissionCreateRequest();
        request.setPatientId(patientId);
        request.setBedId(bedId);
        request.setAttendingDoctorId(UUID.randomUUID());
        request.setAdmissionReason("Typhoid fever");

        assertThatThrownBy(() -> admissionService.admitPatient(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PATIENT_ALREADY_ADMITTED");
    }

    @Test
    void admitPatient_whenBedNotFound_throwsResourceNotFoundException() {
        when(bedRepository.findById(bedId)).thenReturn(Optional.empty());

        AdmissionCreateRequest request = new AdmissionCreateRequest();
        request.setPatientId(patientId);
        request.setBedId(bedId);
        request.setAttendingDoctorId(UUID.randomUUID());
        request.setAdmissionReason("Typhoid fever");

        assertThatThrownBy(() -> admissionService.admitPatient(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Bed not found");
    }

    // ─── getAdmissionById ─────────────────────────────────────────────────────────

    @Test
    void getAdmissionById_whenFound_returnsDto() {
        when(admissionRepository.findById(admissionId)).thenReturn(Optional.of(savedAdmission));
        when(bedRepository.findById(bedId)).thenReturn(Optional.of(availableBed));
        when(wardRepository.findById(wardId)).thenReturn(Optional.of(ward));
        when(serviceChargeRepository.findByAdmissionIdOrderByProvidedAtAsc(admissionId))
                .thenReturn(List.of());

        AdmissionDto result = admissionService.getAdmissionById(admissionId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(admissionId);
        assertThat(result.getPatientName()).isEqualTo("Andrea Lalema");
        assertThat(result.getWardName()).isEqualTo("Male General Ward");
    }

    @Test
    void getAdmissionById_whenNotFound_throwsResourceNotFoundException() {
        when(admissionRepository.findById(admissionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> admissionService.getAdmissionById(admissionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Admission not found");
    }

    // ─── dischargePatient ─────────────────────────────────────────────────────────

    @Test
    void dischargePatient_whenAdmitted_dischargесAndPublishesEvent() {
        when(admissionRepository.findById(admissionId)).thenReturn(Optional.of(savedAdmission));
        when(admissionRepository.save(any(Admission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bedRepository.findById(bedId)).thenReturn(Optional.of(availableBed));
        when(bedRepository.save(any(Bed.class))).thenReturn(availableBed);
        when(wardRepository.findById(wardId)).thenReturn(Optional.of(ward));
        when(serviceChargeRepository.findByAdmissionIdOrderByProvidedAtAsc(admissionId))
                .thenReturn(List.of());
        doNothing().when(billingWardEventPublisher).publishBillingWard(
                any(), any(), any(), any(), any());

        DischargeRequest request = new DischargeRequest();
        request.setDischargeNotes("Recovered well");
        request.setDischargeDiagnosis("Typhoid Fever — resolved");

        AdmissionDto result = admissionService.dischargePatient(admissionId, request);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("discharged");
        assertThat(result.getDischargeNotes()).isEqualTo("Recovered well");
        verify(billingWardEventPublisher).publishBillingWard(any(), any(), any(), any(), any());
        verify(bedRepository).save(any(Bed.class));
    }

    @Test
    void dischargePatient_whenAlreadyDischarged_throwsBusinessException() {
        savedAdmission.setStatus("discharged");
        when(admissionRepository.findById(admissionId)).thenReturn(Optional.of(savedAdmission));

        assertThatThrownBy(() -> admissionService.dischargePatient(admissionId, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ALREADY_DISCHARGED");
    }

    @Test
    void dischargePatient_withServices_computesTotalAndPublishesEvent() {
        WardServiceCharge charge1 = WardServiceCharge.builder()
                .id(UUID.randomUUID())
                .admissionId(admissionId)
                .serviceName("Daily Bed Charge")
                .serviceType("bed_charge")
                .quantity(3)
                .unitPrice(new BigDecimal("2500.00"))
                .totalPrice(new BigDecimal("7500.00"))
                .providedAt(ZonedDateTime.now().minusDays(2))
                .build();

        WardServiceCharge charge2 = WardServiceCharge.builder()
                .id(UUID.randomUUID())
                .admissionId(admissionId)
                .serviceName("IV Ceftriaxone")
                .serviceType("medication")
                .quantity(6)
                .unitPrice(new BigDecimal("450.00"))
                .totalPrice(new BigDecimal("2700.00"))
                .providedAt(ZonedDateTime.now().minusDays(1))
                .build();

        when(admissionRepository.findById(admissionId)).thenReturn(Optional.of(savedAdmission));
        when(admissionRepository.save(any(Admission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bedRepository.findById(bedId)).thenReturn(Optional.of(availableBed));
        when(bedRepository.save(any(Bed.class))).thenReturn(availableBed);
        when(wardRepository.findById(wardId)).thenReturn(Optional.of(ward));
        when(serviceChargeRepository.findByAdmissionIdOrderByProvidedAtAsc(admissionId))
                .thenReturn(List.of(charge1, charge2));
        doNothing().when(billingWardEventPublisher).publishBillingWard(
                any(), any(), any(), any(), any());

        AdmissionDto result = admissionService.dischargePatient(admissionId, null);

        assertThat(result.getRunningTotal())
                .isEqualByComparingTo(new BigDecimal("10200.00"));
        verify(billingWardEventPublisher).publishBillingWard(any(), any(), any(), any(),
                argThat(total -> ((BigDecimal) total).compareTo(new BigDecimal("10200.00")) == 0));
    }
}
