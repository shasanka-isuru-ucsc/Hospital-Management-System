package com.hms.ward.repository;

import com.hms.ward.entity.Bed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BedRepository extends JpaRepository<Bed, UUID> {
    List<Bed> findByWardIdOrderByBedNumber(UUID wardId);
    List<Bed> findByWardIdAndStatusOrderByBedNumber(UUID wardId, String status);
    List<Bed> findByStatusOrderByBedNumber(String status);
    long countByWardIdAndStatus(UUID wardId, String status);
    boolean existsByWardIdAndBedNumber(UUID wardId, String bedNumber);
}
