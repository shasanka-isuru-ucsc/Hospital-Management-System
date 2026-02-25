package com.hms.staff.service;

import com.hms.staff.dto.AvailableSlotDto;
import com.hms.staff.entity.Doctor;
import com.hms.staff.entity.Schedule;
import com.hms.staff.repository.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @InjectMocks
    private ScheduleService scheduleService;

    @Test
    void getAvailability_success() {
        UUID doctorId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 2, 23); // Monday
        int dayOfWeek = 1; // Monday

        Schedule schedule = Schedule.builder()
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .slotDurationMinutes(30)
                .isActive(true)
                .build();

        when(scheduleRepository.findByDoctorIdAndDayOfWeekAndIsActiveTrue(doctorId, dayOfWeek))
                .thenReturn(Collections.singletonList(schedule));

        List<AvailableSlotDto> results = scheduleService.getAvailability(doctorId, date);

        assertEquals(2, results.size());
        assertEquals("08:00", results.get(0).getFromTime());
        assertEquals("08:30", results.get(0).getToTime());
        assertEquals("08:30", results.get(1).getFromTime());
        assertEquals("09:00", results.get(1).getToTime());
    }
}
