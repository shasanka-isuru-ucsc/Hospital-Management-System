package com.hms.reception.repository;

import com.hms.reception.entity.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

       @Query("SELECT a FROM Appointment a WHERE " +
                     "(a.doctorId = COALESCE(:doctorId, a.doctorId)) AND " +
                     "(a.patient.id = COALESCE(:patientId, a.patient.id)) AND " +
                     "(a.appointmentDate = COALESCE(:date, a.appointmentDate)) AND " +
                     "(a.status = COALESCE(:status, a.status))")
       Page<Appointment> findWithFilters(@Param("doctorId") UUID doctorId,
                     @Param("patientId") UUID patientId,
                     @Param("date") LocalDate date,
                     @Param("status") String status,
                     Pageable pageable);

       @Query("SELECT a FROM Appointment a WHERE a.doctorId = :doctorId AND a.appointmentDate = :date AND a.status IN ('scheduled', 'ongoing', 'rescheduled') "
                     +
                     "AND ((a.fromTime < :toTime AND a.toTime > :fromTime)) " +
                     "AND (CAST(:excludeId AS uuid) IS NULL OR a.id != :excludeId)")
       List<Appointment> findOverlappingAppointments(@Param("doctorId") UUID doctorId,
                     @Param("date") LocalDate date,
                     @Param("fromTime") LocalTime fromTime,
                     @Param("toTime") LocalTime toTime,
                     @Param("excludeId") UUID excludeId);
}
