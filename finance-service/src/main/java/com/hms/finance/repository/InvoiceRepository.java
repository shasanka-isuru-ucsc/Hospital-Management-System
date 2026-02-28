package com.hms.finance.repository;

import com.hms.finance.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findBySessionReferenceId(UUID sessionReferenceId);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(i.invoiceNumber, 10) AS int)), 0) FROM Invoice i " +
           "WHERE i.invoiceNumber LIKE CONCAT('INV-', :year, '-%')")
    int findMaxSequenceForYear(@Param("year") String year);

    @Query("SELECT COUNT(DISTINCT i.patientId) FROM Invoice i WHERE i.createdAt >= :from AND i.createdAt < :to")
    long countDistinctPatientsBetween(
            @Param("from") java.time.ZonedDateTime from,
            @Param("to") java.time.ZonedDateTime to);

    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i " +
           "WHERE i.paymentStatus IN ('paid', 'partial') AND i.createdAt >= :from AND i.createdAt < :to")
    java.math.BigDecimal sumEarningsBetween(
            @Param("from") java.time.ZonedDateTime from,
            @Param("to") java.time.ZonedDateTime to);
}
