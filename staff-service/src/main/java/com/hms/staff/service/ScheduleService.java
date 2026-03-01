package com.hms.staff.service;

import com.hms.staff.dto.AvailableSlotDto;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final String[] DAY_NAMES = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ScheduleRepository scheduleRepository;
    private final DoctorRepository doctorRepository;

    // ─── Get Schedule ─────────────────────────────────────────────────────────

    public List<ScheduleDto> getDoctorSchedule(UUID doctorId) {
        doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));

        return scheduleRepository.findByDoctorIdAndIsActiveTrue(doctorId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ─── Add Schedule Entry ───────────────────────────────────────────────────

    @Transactional
    public ScheduleDto addScheduleEntry(UUID doctorId, ScheduleCreateRequest request) {
        doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));

        LocalTime startTime = parseTime(request.getStartTime());
        LocalTime endTime = parseTime(request.getEndTime());

        if (!endTime.isAfter(startTime)) {
            throw new BusinessException("INVALID_TIME_RANGE",
                    "end_time must be after start_time", 400);
        }

        long durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes();
        if (durationMinutes < request.getSlotDurationMinutes()) {
            throw new BusinessException("INVALID_SLOT_DURATION",
                    "Total session duration must be at least one slot duration", 400);
        }

        Schedule schedule = Schedule.builder()
                .doctorId(doctorId)
                .dayOfWeek(request.getDayOfWeek())
                .startTime(startTime)
                .endTime(endTime)
                .slotDurationMinutes(request.getSlotDurationMinutes())
                .maxPatients(request.getMaxPatients())
                .isActive(true)
                .build();

        Schedule saved = scheduleRepository.save(schedule);
        log.info("Added schedule entry for doctor {} on day {}", doctorId, request.getDayOfWeek());
        return toDto(saved);
    }

    // ─── Update Schedule Entry ────────────────────────────────────────────────

    @Transactional
    public ScheduleDto updateScheduleEntry(UUID scheduleId, ScheduleUpdateRequest request) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule entry not found: " + scheduleId));

        if (request.getStartTime() != null) schedule.setStartTime(parseTime(request.getStartTime()));
        if (request.getEndTime() != null) schedule.setEndTime(parseTime(request.getEndTime()));
        if (request.getSlotDurationMinutes() != null) schedule.setSlotDurationMinutes(request.getSlotDurationMinutes());
        if (request.getMaxPatients() != null) schedule.setMaxPatients(request.getMaxPatients());
        if (request.getIsActive() != null) schedule.setIsActive(request.getIsActive());

        if (!schedule.getEndTime().isAfter(schedule.getStartTime())) {
            throw new BusinessException("INVALID_TIME_RANGE", "end_time must be after start_time", 400);
        }

        Schedule saved = scheduleRepository.save(schedule);
        return toDto(saved);
    }

    // ─── Delete Schedule Entry ────────────────────────────────────────────────

    @Transactional
    public void deleteScheduleEntry(UUID scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule entry not found: " + scheduleId));
        scheduleRepository.delete(schedule);
        log.info("Deleted schedule entry: {}", scheduleId);
    }

    // ─── Get Availability ─────────────────────────────────────────────────────

    public DoctorAvailabilityDto getDoctorAvailability(UUID doctorId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));

        // Java DayOfWeek: 1=Monday ... 7=Sunday. Spec: 0=Sunday ... 6=Saturday
        DayOfWeek javaDow = date.getDayOfWeek();
        int dayOfWeek = javaDow == DayOfWeek.SUNDAY ? 0 : javaDow.getValue();

        Schedule schedule = scheduleRepository.findByDoctorIdAndDayOfWeekAndIsActiveTrue(doctorId, dayOfWeek)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor has no schedule for " + DAY_NAMES[dayOfWeek]));

        List<AvailableSlotDto> slots = generateSlots(schedule);

        return DoctorAvailabilityDto.builder()
                .doctorId(doctorId)
                .doctorName("Dr. " + doctor.getFirstName() + " " + doctor.getLastName())
                .date(date)
                .slots(slots)
                .build();
    }

    // ─── Slot Generation ──────────────────────────────────────────────────────

    private List<AvailableSlotDto> generateSlots(Schedule schedule) {
        List<AvailableSlotDto> slots = new ArrayList<>();
        LocalTime current = schedule.getStartTime();
        LocalTime limit = schedule.getEndTime();
        int duration = schedule.getSlotDurationMinutes();

        while (!current.plusMinutes(duration).isAfter(limit)) {
            LocalTime next = current.plusMinutes(duration);
            slots.add(AvailableSlotDto.builder()
                    .fromTime(current.format(TIME_FMT))
                    .toTime(next.format(TIME_FMT))
                    .isAvailable(true)
                    .appointmentId(null)
                    .build());
            current = next;
        }

        return slots;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private LocalTime parseTime(String time) {
        try {
            return LocalTime.parse(time, TIME_FMT);
        } catch (Exception e) {
            throw new BusinessException("INVALID_TIME_FORMAT",
                    "Invalid time format '" + time + "'. Use HH:MM (e.g. 08:00)", 400);
        }
    }

    private ScheduleDto toDto(Schedule schedule) {
        return ScheduleDto.builder()
                .id(schedule.getId())
                .doctorId(schedule.getDoctorId())
                .dayOfWeek(schedule.getDayOfWeek())
                .dayName(DAY_NAMES[schedule.getDayOfWeek()])
                .startTime(schedule.getStartTime().format(TIME_FMT))
                .endTime(schedule.getEndTime().format(TIME_FMT))
                .slotDurationMinutes(schedule.getSlotDurationMinutes())
                .maxPatients(schedule.getMaxPatients())
                .isActive(schedule.getIsActive())
                .build();
    }
}
