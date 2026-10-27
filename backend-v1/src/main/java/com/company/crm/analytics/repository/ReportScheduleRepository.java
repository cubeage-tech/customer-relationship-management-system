package com.company.crm.analytics.repository;

import com.company.crm.analytics.entity.ReportSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, Long> {

    List<ReportSchedule> findByTenantIdOrderByNameAsc(Long tenantId);

    List<ReportSchedule> findByTenantIdAndOwnerIdOrderByNameAsc(Long tenantId, Long ownerId);

    @Query("select s.id from ReportSchedule s where s.active = true and s.nextRunAt <= :now order by s.nextRunAt")
    List<Long> findDueIds(@Param("now") LocalDateTime now);

    /** REP-SCH: claims one due run by moving its next run on (returns 0 if another run already took it). */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update ReportSchedule s set s.nextRunAt = :next, s.lastRunAt = :now"
            + " where s.id = :id and s.active = true and s.nextRunAt = :due")
    int claimRun(@Param("id") Long id, @Param("due") LocalDateTime due, @Param("next") LocalDateTime next,
                 @Param("now") LocalDateTime now);
}
