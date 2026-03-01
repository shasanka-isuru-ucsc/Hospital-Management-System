package com.hms.staff.service;

import com.hms.staff.dto.DoctorAvailabilityDto;
import com.hms.staff.dto.ScheduleCreateRequest;
import com.hms.staff.dto.ScheduleDto;
import com.hms.staff.dto.ScheduleUpdateRequest;
import com.hms.staff.entity.Doctor;
import com.hms.staff.entity.Schedule;
import com.hms.staff.exception.BusinessException;
import com.hms.staff.exception.ResourceNotFoundException;
import com.hms.staff.repository.DoctorRepository;
import com.hms.staff.repository.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private DoctorRepository doctorRepository;

    @InjectMocks private ScheduleService scheduleService;

    @Test
    void getDoctorSchedule_doctorNotFound_throws404() {
        UUID doctorId = UUID.randomUUID();
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.getDoctorSchedule(doctorId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getDoctorSchedule_returnsActiveEntries() {
        UUID doctorId = UUID.randomUUID();
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(buildDoctor(doctorId)));
        Schedule s = Schedule.builder()
                .id(UUID.randomUUID()).doctorId(doctorId).dayOfWeek(1)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
                .slotDurationMinutes(15).maxPatients(16).isActive(true).build();
        when(scheduleRepository.findByDoctorIdAndIsActiveTrue(doctorId)).thenReturn(List.of(s));

        List<ScheduleDto> result = scheduleService.getDoctorSchedule(doctorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDayName()).isEqualTo("Monday");
        assertThat(result.get(0).getStartTime()).isEqualTo("08:00");
    }

    @Test
    void addScheduleEntry_invalidTimeRange_throws400() {
        UUID doctorId = UUID.randomUUID();
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(buildDoctor(doctorId)));

        ScheduleCreateRequest req = new ScheduleCreateRequest();
        req.setDayOfWeek(1);
        req.setStartTime("12:00");
        req.setEndTime("08:00"); // end before start
        req.setSlotDurationMinutes(15);
        req.setMaxPatients(10);

        assertThatThrownBy(() -> scheduleService.addScheduleEntry(doctorId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("end_time must be after start_time")
                .extracting("statusCode").isEqualTo(400);
    }

    @Test
    void addScheduleEntry_success_generatesCorrectSlotCount() {
        UUID doctorId = UUID.randomUUID();
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(buildDoctor(doctorId)));

        ScheduleCreateRequest req = new ScheduleCreateRequest();
        req.setDayOfWeek(1);
        req.setStartTime("08:00");
        req.setEndTime("12:00");
        req.setSlotDurationMinutes(15);
        req.setMaxPatients(16);

        Schedule savedSchedule = Schedule.builder()
                .id(UUID.randomUUID()).doctorId(doctorId).dayOfWeek(1)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
                .slotDurationMinutes(15).maxPatients(16).isActive(true).build();
        when(scheduleRepository.save(any())).thenReturn(savedSchedule);

        ScheduleDto result = scheduleService.addScheduleEntry(doctorId, req);

        assertThat(result.getDayOfWeek()).isEqualTo(1);
        assertThat(result.getMaxPatients()).isEqualTo(16);
    }

    @Test
    void getDoctorAvailability_noScheduleForDay_throws404() {
        UUID doctorId = UUID.randomUUID();
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(buildDoctor(doctorId)));
        // Monday = dayOfWeek 1
        LocalDate monday = LocalDate.of(2026, 3, 2); // This is a Monday
        when(scheduleRepository.findByDoctorIdAndDayOfWeekAndIsActiveTrue(doctorId, 1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.getDoctorAvailability(doctorId, monday))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no schedule");
    }

    @Test
    void getDoctorAvailability_generatesCorrectSlots() {
        UUID doctorId = UUID.randomUUID();
        Doctor doctor = buildDoctor(doctorId);
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor));

        // Monday March 2, 2026
        LocalDate monday = LocalDate.of(2026, 3, 2);
        Schedule schedule = Schedule.builder()
                .id(UUID.randomUUID()).doctorId(doctorId).dayOfWeek(1)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(10, 0))
                .slotDurationMinutes(30).maxPatients(4).isActive(true).build();
        when(scheduleRepository.findByDoctorIdAndDayOfWeekAndIsActiveTrue(doctorId, 1))
                .thenReturn(Optional.of(schedule));

        DoctorAvailabilityDto result = scheduleService.getDoctorAvailability(doctorId, monday);

        // 8:00-10:00 with 30-min slots = 4 slots
        assertThat(result.getSlots()).hasSize(4);
        assertThat(result.getSlots().get(0).getFromTime()).isEqualTo("08:00");
        assertThat(result.getSlots().get(0).getToTime()).isEqualTo("08:30");
        assertThat(result.getSlots().get(3).getFromTime()).isEqualTo("09:30");
        assertThat(result.getSlots().get(3).getToTime()).isEqualTo("10:00");
        assertThat(result.getSlots()).allMatch(s -> s.getIsAvailable());
    }

    @Test
    void deleteScheduleEntry_notFound_throws404() {
        UUID scheduleId = UUID.randomUUID();
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.deleteScheduleEntry(scheduleId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateScheduleEntry_success() {
        UUID scheduleId = UUID.randomUUID();
        Schedule existing = Schedule.builder()
                .id(scheduleId).doctorId(UUID.randomUUID()).dayOfWeek(1)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
                .slotDurationMinutes(15).maxPatients(16).isActive(true).build();
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existing));
        when(scheduleRepository.save(any())).thenReturn(existing);

        ScheduleUpdateRequest req = new ScheduleUpdateRequest();
        req.setMaxPatients(20);
        req.setIsActive(false);

        ScheduleDto result = scheduleService.updateScheduleEntry(scheduleId, req);

        assertThat(result.getMaxPatients()).isEqualTo(20);
        assertThat(result.getIsActive()).isFalse();
    }

    private Doctor buildDoctor(UUID id) {
        return Doctor.builder()
                .id(id).firstName("Jenny").lastName("Smith")
                .username("dr.jenny").email("jenny@h.com").status("active")
                .build();
    }
}
