package com.company.crm.meeting.entity;

import com.company.crm.customer.entity.Customer;
import com.company.crm.lead.entity.Lead;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** FR-5.5: a meeting with a customer or lead, with an optional reminder and an iCalendar identity. */
@Entity
@Table(name = "meetings")
@Getter
@Setter
@NoArgsConstructor
public class Meeting {

    public static final String SCHEDULED = "scheduled";
    public static final String CANCELLED = "cancelled";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(length = 300)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    @Column(nullable = false, length = 20)
    private String status = SCHEDULED;

    /** FR-5.5b: remind this many minutes before the start; null = no reminder. */
    @Column(name = "reminder_minutes")
    private Integer reminderMinutes;

    /** FR-5.5b: also send the invite, updates and reminder to the customer/lead contact. */
    @Column(name = "include_customer", nullable = false)
    private boolean includeCustomer;

    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    /** FR-M.3: the meeting report — outcome (positive | neutral | negative | no_show), notes, next steps. */
    @Column(name = "report_outcome", length = 20)
    private String reportOutcome;

    @Column(name = "report_notes", columnDefinition = "TEXT")
    private String reportNotes;

    @Column(name = "next_steps", columnDefinition = "TEXT")
    private String nextSteps;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by")
    private User reportedBy;

    /** FR-5.5c: stable iCalendar UID; the sequence increases on every change so calendars update. */
    @Column(name = "ical_uid", nullable = false, length = 100, updatable = false)
    private String icalUid;

    @Column(name = "ical_sequence", nullable = false)
    private int icalSequence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isCancelled() {
        return CANCELLED.equals(status);
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
