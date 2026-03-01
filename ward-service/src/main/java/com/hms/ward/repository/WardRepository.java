package com.hms.ward.repository;

import com.hms.ward.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WardRepository extends JpaRepository<Ward, UUID> {
    List<Ward> findByIsActive(Boolean isActive);
    List<Ward> findByTypeAndIsActive(String type, Boolean isActive);
    List<Ward> findByType(String type);
}
