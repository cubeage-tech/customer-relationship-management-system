package com.company.crm.meeting.repository;

import com.company.crm.meeting.entity.Meeting;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    @EntityGraph(attributePaths = {"organizer", "customer", "lead"})
    Optional<Meeting> findByIdAndTenantId(Long id, Long tenantId);

    @EntityGraph(attributePaths = {"organizer", "customer", "lead"})
    List<Meeting> findByTenantIdAndCustomerIdOrderByStartsAtDesc(Long tenantId, Long customerId);

    @EntityGraph(attributePaths = {"organizer", "customer", "lead"})
    List<Meeting> findByTenantIdAndLeadIdOrderByStartsAtDesc(Long tenantId, Long leadId);

    /** The organizer's upcoming meetings (my agenda) — scheduled, ending after {@code from}. */
    @EntityGraph(attributePaths = {"organizer", "customer", "lead"})
    List<Meeting> findByOrganizerIdAndStatusAndEndsAtAfterOrderByStartsAtAsc(Long organizerId, String status, LocalDateTime from);

    /** FR-5.5c feed: the organizer's meetings from {@code from} on, cancelled included (calendars remove them). */
    @EntityGraph(attributePaths = {"organizer", "customer", "lead"})
    List<Meeting> findByOrganizerIdAndStartsAtAfterOrderByStartsAtAsc(Long organizerId, LocalDateTime from);

    /** FR-5.5b: scheduled meetings whose reminder time has come and that haven't been reminded. */
    @Query(value = "SELECT id FROM meetings WHERE status = 'scheduled' AND reminder_minutes IS NOT NULL"
            + " AND reminder_sent_at IS NULL AND starts_at > :now"
            + " AND starts_at - make_interval(mins => reminder_minutes) <= :now ORDER BY starts_at", nativeQuery = true)
    List<Long> findDueReminderIds(@Param("now") LocalDateTime now);

    /** FR-5.5b: claims a reminder (0 if another run already sent it). */
    @Modifying
    @Query("update Meeting m set m.reminderSentAt = :now where m.id = :id and m.reminderSentAt is null")
    int claimReminder(@Param("id") Long id, @Param("now") LocalDateTime now);
}
