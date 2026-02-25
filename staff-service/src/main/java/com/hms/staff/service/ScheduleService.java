package com.hms.staff.service;

import com.hms.staff.dto.AvailableSlotDto;
import com.hms.staff.dto.ScheduleDto;
import com.hms.staff.entity.Doctor;
import com.hms.staff.entity.Schedule;
import com.hms.staff.exception.ResourceNotFoundException;
import com.hms.staff.repository.DoctorRepository;
import com.hms.staff.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final DoctorRepository doctorRepository;

    public List<ScheduleDto> getDoctorSchedule(UUID doctorId) {
        return scheduleRepository.findByDoctorIdAndIsActiveTrue(doctorId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ScheduleDto addScheduleEntry(UUID doctorId, ScheduleDto dto) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id: " + doctorId));

        Schedule schedule = Schedule.builder()
                .doctor(doctor)
                .dayOfWeek(dto.getDayOfWeek())
                .startTime(LocalTime.parse(dto.getStartTime()))
                .endTime(LocalTime.parse(dto.getEndTime()))
                .slotDurationMinutes(dto.getSlotDurationMinutes())
                .maxPatients(dto.getMaxPatients())
                .isActive(true)
                .build();

        return convertToDto(scheduleRepository.save(schedule));
    }

    @Transactional
    public ScheduleDto updateScheduleEntry(UUID id, ScheduleDto dto) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found with id: " + id));

        if (dto.getStartTime() != null)
            schedule.setStartTime(LocalTime.parse(dto.getStartTime()));
        if (dto.getEndTime() != null)
            schedule.setEndTime(LocalTime.parse(dto.getEndTime()));
        if (dto.getSlotDurationMinutes() != null)
            schedule.setSlotDurationMinutes(dto.getSlotDurationMinutes());
        if (dto.getMaxPatients() != null)
            schedule.setMaxPatients(dto.getMaxPatients());
        schedule.setActive(dto.isActive());

        return convertToDto(scheduleRepository.save(schedule));
    }

    @Transactional
    public void deleteScheduleEntry(UUID id) {
        if (!scheduleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Schedule not found with id: " + id);
        }
        scheduleRepository.deleteById(id);
    }

    public List<AvailableSlotDto> getAvailability(UUID doctorId, LocalDate date) {
        int dayOfWeek = date.getDayOfWeek().getValue() % 7; // Sunday=0 in API, Sunday=7 in Java
        List<Schedule> schedules = scheduleRepository.findByDoctorIdAndDayOfWeekAndIsActiveTrue(doctorId, dayOfWeek);

        if (schedules.isEmpty()) {
            throw new ResourceNotFoundException("No schedule found for this doctor on " + date);
        }

        List<AvailableSlotDto> allSlots = new ArrayList<>();
        for (Schedule schedule : schedules) {
            LocalTime current = schedule.getStartTime();
            while (current.plusMinutes(schedule.getSlotDurationMinutes()).isBefore(schedule.getEndTime()) ||
                    current.plusMinutes(schedule.getSlotDurationMinutes()).equals(schedule.getEndTime())) {

                LocalTime next = current.plusMinutes(schedule.getSlotDurationMinutes());
                allSlots.add(AvailableSlotDto.builder()
                        .fromTime(current.toString())
                        .toTime(next.toString())
                        .isAvailable(true) // Checking booked slots would require calling Reception Service (Mocked here
                                           // as always true for now)
                        .build());
                current = next;
            }
        }
        return allSlots;
    }

    private ScheduleDto convertToDto(Schedule schedule) {
        String dayName = LocalDate.now()
                .with(java.time.DayOfWeek.of(schedule.getDayOfWeek() == 0 ? 7 : schedule.getDayOfWeek()))
                .getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        return ScheduleDto.builder()
                .id(schedule.getId())
                .doctorId(schedule.getDoctor().getId())
                .dayOfWeek(schedule.getDayOfWeek())
                .dayName(dayName)
                .startTime(schedule.getStartTime().toString())
                .endTime(schedule.getEndTime().toString())
                .slotDurationMinutes(schedule.getSlotDurationMinutes())
                .maxPatients(schedule.getMaxPatients())
                .isActive(schedule.isActive())
                .build();
    }
}
