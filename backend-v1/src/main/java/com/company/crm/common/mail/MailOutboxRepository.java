package com.company.crm.common.mail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface MailOutboxRepository extends JpaRepository<MailOutboxEntry, Long> {

    @Query("select m.id from MailOutboxEntry m where m.status = 'pending' and m.nextAttemptAt <= :now order by m.nextAttemptAt")
    List<Long> findDueIds(@Param("now") LocalDateTime now);

    /**
     * Leases one due entry for a retry by pushing its next attempt out (returns 0 if another
     * instance already took it), so two servers never send the same email.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update MailOutboxEntry m set m.nextAttemptAt = :lease where m.id = :id and m.status = 'pending' and m.nextAttemptAt = :due")
    int lease(@Param("id") Long id, @Param("due") LocalDateTime due, @Param("lease") LocalDateTime lease);
}
