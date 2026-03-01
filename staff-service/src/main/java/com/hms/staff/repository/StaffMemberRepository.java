package com.hms.staff.repository;

import com.hms.staff.entity.StaffMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StaffMemberRepository extends JpaRepository<StaffMember, UUID> {
    List<StaffMember> findByRoleAndStatus(String role, String status);
    List<StaffMember> findByStatus(String status);
}
