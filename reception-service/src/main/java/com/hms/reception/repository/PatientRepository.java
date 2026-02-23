package com.hms.reception.repository;

import com.hms.reception.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

       Optional<Patient> findByMobile(String mobile);

       @Query("SELECT p FROM Patient p WHERE " +
                     "(p.status = COALESCE(:status, p.status)) AND " +
                     "(:search IS NULL OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
                     "OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
                     "OR p.mobile LIKE CONCAT('%', :search, '%'))")
       Page<Patient> findAllByFilters(@Param("search") String search, @Param("status") String status,
                     Pageable pageable);

       @Query("SELECT p FROM Patient p WHERE " +
                     "LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                     "LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                     "p.mobile LIKE CONCAT('%', :q, '%')")
       List<Patient> searchByNameOrMobile(@Param("q") String query);

       @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(patient_number, 2) AS INTEGER)), 0) FROM patients", nativeQuery = true)
       Long findMaxPatientNumber();
}
