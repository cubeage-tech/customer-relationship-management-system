package com.company.crm.analytics.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** REP-SCH: a report generated on a schedule and emailed, run as its owner. */
@Entity
@Table(name = "report_schedules")
@Getter
@Setter
@NoArgsConstructor
public class ReportSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 150)
    private String name;

    /** A {@link com.company.crm.analytics.service.ReportType} code. */
    @Column(name = "report_type", nullable = false, length = 30)
    private String reportType;

    /** pdf | xlsx */
    @Column(nullable = false, length = 10)
    private String format;

    /** daily | weekly | monthly */
    @Column(nullable = false, length = 10)
    private String frequency;

    @Column(name = "hour_of_day", nullable = false)
    private int hourOfDay = 7;

    /** Comma-separated emails of users in the same tenant. */
    @Column(nullable = false, length = 1000)
    private String recipients;

    private String industry;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "next_run_at", nullable = false)
    private LocalDateTime nextRunAt;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    /** sent | failed */
    @Column(name = "last_status", length = 10)
    private String lastStatus;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
