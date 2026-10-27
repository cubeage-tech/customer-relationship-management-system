package com.company.crm.salesforce.repository;

import com.company.crm.salesforce.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    /** The user's current check-in (at most one — enforced by a unique index). */
    Optional<AttendanceRecord> findByUserIdAndCheckOutAtIsNull(Long userId);

    List<AttendanceRecord> findTop30ByUserIdOrderByCheckInAtDesc(Long userId);

    @EntityGraph(attributePaths = "user")
    List<AttendanceRecord> findByTenantIdAndUserIdInAndCheckInAtGreaterThanEqualAndCheckInAtLessThanOrderByCheckInAtDesc(
            Long tenantId, Collection<Long> userIds, LocalDateTime from, LocalDateTime to);
}
