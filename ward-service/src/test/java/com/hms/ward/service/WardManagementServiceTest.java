package com.hms.ward.service;

import com.hms.ward.dto.WardCreateRequest;
import com.hms.ward.dto.WardDto;
import com.hms.ward.entity.Bed;
import com.hms.ward.entity.Ward;
import com.hms.ward.repository.BedRepository;
import com.hms.ward.repository.WardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WardManagementServiceTest {

    @Mock
    private WardRepository wardRepository;

    @Mock
    private BedRepository bedRepository;

    @InjectMocks
    private WardManagementService wardManagementService;

    private UUID wardId;
    private Ward savedWard;

    @BeforeEach
    void setUp() {
        wardId = UUID.randomUUID();

        savedWard = Ward.builder()
                .id(wardId)
                .name("Male General Ward")
                .type("general")
                .capacity(5)
                .isActive(true)
                .build();
    }

    // ─── createWard ───────────────────────────────────────────────────────────────

    @Test
    void createWard_generatesCorrectNumberOfBeds() {
        when(wardRepository.save(any(Ward.class))).thenReturn(savedWard);
        when(bedRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(bedRepository.countByWardIdAndStatus(wardId, "occupied")).thenReturn(0L);
        when(bedRepository.countByWardIdAndStatus(wardId, "maintenance")).thenReturn(0L);
        when(bedRepository.countByWardIdAndStatus(wardId, "reserved")).thenReturn(0L);
        when(bedRepository.countByWardIdAndStatus(wardId, "available")).thenReturn(5L);

        WardCreateRequest request = new WardCreateRequest();
        request.setName("Male General Ward");
        request.setType("general");
        request.setCapacity(5);

        WardDto result = wardManagementService.createWard(request);

        // Verify 5 beds were created
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Bed>> bedCaptor = ArgumentCaptor.forClass(List.class);
        verify(bedRepository).saveAll(bedCaptor.capture());

        List<Bed> capturedBeds = bedCaptor.getValue();
        assertThat(capturedBeds).hasSize(5);
        assertThat(capturedBeds.get(0).getBedNumber()).isEqualTo("M-101");
        assertThat(capturedBeds.get(4).getBedNumber()).isEqualTo("M-105");
        assertThat(capturedBeds).allMatch(bed -> "available".equals(bed.getStatus()));
    }

    @Test
    void createWard_bedPrefixBasedOnWardName() {
        Ward femaleWard = Ward.builder()
                .id(wardId)
                .name("Female General Ward")
                .type("general")
                .capacity(3)
                .isActive(true)
                .build();

        when(wardRepository.save(any(Ward.class))).thenReturn(femaleWard);
        when(bedRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(bedRepository.countByWardIdAndStatus(wardId, "occupied")).thenReturn(0L);
        when(bedRepository.countByWardIdAndStatus(wardId, "maintenance")).thenReturn(0L);
        when(bedRepository.countByWardIdAndStatus(wardId, "reserved")).thenReturn(0L);
        when(bedRepository.countByWardIdAndStatus(wardId, "available")).thenReturn(3L);

        WardCreateRequest request = new WardCreateRequest();
        request.setName("Female General Ward");
        request.setType("general");
        request.setCapacity(3);

        wardManagementService.createWard(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Bed>> bedCaptor = ArgumentCaptor.forClass(List.class);
        verify(bedRepository).saveAll(bedCaptor.capture());

        List<Bed> capturedBeds = bedCaptor.getValue();
        assertThat(capturedBeds.get(0).getBedNumber()).startsWith("F-");
    }

    @Test
    void createWard_returnsWardDtoWithOccupancyCounts() {
        when(wardRepository.save(any(Ward.class))).thenReturn(savedWard);
        when(bedRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(bedRepository.countByWardIdAndStatus(wardId, "occupied")).thenReturn(2L);
        when(bedRepository.countByWardIdAndStatus(wardId, "maintenance")).thenReturn(1L);
        when(bedRepository.countByWardIdAndStatus(wardId, "reserved")).thenReturn(0L);
        when(bedRepository.countByWardIdAndStatus(wardId, "available")).thenReturn(2L);

        WardCreateRequest request = new WardCreateRequest();
        request.setName("Male General Ward");
        request.setType("general");
        request.setCapacity(5);

        WardDto result = wardManagementService.createWard(request);

        assertThat(result.getOccupied()).isEqualTo(2L);
        assertThat(result.getAvailable()).isEqualTo(2L);
        assertThat(result.getMaintenance()).isEqualTo(1L);
        assertThat(result.getCapacity()).isEqualTo(5);
    }

    // ─── listWards ────────────────────────────────────────────────────────────────

    @Test
    void listWards_withNoFilters_returnsAll() {
        when(wardRepository.findAll()).thenReturn(List.of(savedWard));
        when(bedRepository.countByWardIdAndStatus(wardId, "occupied")).thenReturn(0L);
        when(bedRepository.countByWardIdAndStatus(wardId, "maintenance")).thenReturn(0L);
        when(bedRepository.countByWardIdAndStatus(wardId, "reserved")).thenReturn(0L);
        when(bedRepository.countByWardIdAndStatus(wardId, "available")).thenReturn(5L);

        List<WardDto> result = wardManagementService.listWards(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Male General Ward");
    }

    @Test
    void listWards_withTypeFilter_returnsFilteredList() {
        when(wardRepository.findByType("icu")).thenReturn(List.of());

        List<WardDto> result = wardManagementService.listWards("icu", null);

        assertThat(result).isEmpty();
        verify(wardRepository).findByType("icu");
    }

    @Test
    void listWards_withActiveFilter_returnsActiveWards() {
        when(wardRepository.findByIsActive(true)).thenReturn(List.of(savedWard));
        when(bedRepository.countByWardIdAndStatus(wardId, "occupied")).thenReturn(0L);
        when(bedRepository.countByWardIdAndStatus(wardId, "maintenance")).thenReturn(0L);
        when(bedRepository.countByWardIdAndStatus(wardId, "reserved")).thenReturn(0L);
        when(bedRepository.countByWardIdAndStatus(wardId, "available")).thenReturn(5L);

        List<WardDto> result = wardManagementService.listWards(null, true);

        assertThat(result).hasSize(1);
        verify(wardRepository).findByIsActive(true);
    }
}
