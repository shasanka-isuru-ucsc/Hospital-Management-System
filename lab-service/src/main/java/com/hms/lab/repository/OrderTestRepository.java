package com.hms.lab.repository;

import com.hms.lab.entity.OrderTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderTestRepository extends JpaRepository<OrderTest, UUID> {

    List<OrderTest> findByOrderId(UUID orderId);

    boolean existsByTestId(UUID testId);
}
