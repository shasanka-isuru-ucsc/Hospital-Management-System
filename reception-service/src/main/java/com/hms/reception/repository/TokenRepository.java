package com.hms.reception.repository;

import com.hms.reception.entity.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TokenRepository extends JpaRepository<Token, UUID> {

    @Query("SELECT COALESCE(MAX(t.tokenNumber), 0) FROM Token t WHERE t.queueType = :queueType AND t.sessionDate = :sessionDate")
    Integer findMaxTokenNumber(@Param("queueType") String queueType, @Param("sessionDate") LocalDate sessionDate);

    boolean existsByPatientIdAndQueueTypeAndSessionDateAndStatusNot(UUID patientId, String queueType, LocalDate sessionDate, String status);

    List<Token> findBySessionDate(LocalDate sessionDate);

    List<Token> findByQueueTypeAndSessionDateOrderByTokenNumberAsc(String queueType, LocalDate sessionDate);
    
    List<Token> findByQueueTypeAndDoctorIdAndSessionDateOrderByTokenNumberAsc(String queueType, UUID doctorId, LocalDate sessionDate);

    @Query("SELECT COUNT(t) FROM Token t WHERE t.queueType = :queueType AND t.sessionDate = :sessionDate AND t.status = 'waiting' AND t.tokenNumber < :tokenNumber")
    Long countWaitingBeforeToken(@Param("queueType") String queueType, @Param("sessionDate") LocalDate sessionDate, @Param("tokenNumber") Integer tokenNumber);
}
