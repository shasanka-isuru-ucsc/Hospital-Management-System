package com.hms.clinical.repository;

import com.hms.clinical.entity.SessionImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionImageRepository extends JpaRepository<SessionImage, UUID> {
    List<SessionImage> findBySessionId(UUID sessionId);
}
