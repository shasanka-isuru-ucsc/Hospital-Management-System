package com.hms.staff.repository;

import com.hms.staff.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {
    List<Schedule> findByDoctorIdAndIsActiveTrue(UUID doctorId);
    List<Schedule> findByDoctorId(UUID doctorId);
    Optional<Schedule> findByDoctorIdAndDayOfWeekAndIsActiveTrue(UUID doctorId, Integer dayOfWeek);
    boolean existsByDoctorIdAndDayOfWeek(UUID doctorId, Integer dayOfWeek);
}
